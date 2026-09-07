package scoring.core;

import scoring.core.dto.AvgMethod;
import scoring.core.dto.BonusItem;
import scoring.core.dto.CandidateDetail;
import scoring.core.dto.CandidateInfo;
import scoring.core.dto.CandidateStatus;
import scoring.core.dto.FinishResult;
import scoring.core.dto.ImportReport;
import scoring.core.dto.OpLogEntry;
import scoring.core.dto.RankRow;
import scoring.core.dto.ScoreItem;
import scoring.core.dto.SystemStats;
import scoring.core.dto.UndoResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 学生组织面试评分系统 —— core 层门面服务（未来 GUI 直接复用的入口类）。
 *
 * <p>职责：名单导入、候选人搜索、面试状态机、评分/最终分计算、补录、附加分、
 * 操作日志与撤销、排名、CSV(UTF-8 BOM) 导出、初始化。本类不依赖任何控制台/UI API。
 *
 * <p>线程模型：本服务面向单进程单实例使用（CLI 一个交互会话 / GUI 一个窗口持有单例），
 * 内部不对并发加锁；GUI 请勿多线程并发调用同一实例。
 */
public final class ScoringService implements AutoCloseable {

    /** 分值域上下界（普通评分与附加分一致：0～100，最多两位小数）。 */
    public static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    public static final BigDecimal MAX_VALUE = new BigDecimal("100");

    private static final int NAME_MAX = 64;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Store store;

    /** @param jdbcUrl H2 JDBC URL，如 {@code jdbc:h2:file:data/scoring}（父目录需已存在）。 */
    public ScoringService(String jdbcUrl) {
        this.store = new Store(jdbcUrl);
    }

    // ------------------------------------------------------------------ 名单导入

    /**
     * 导入名单文件（UTF-8/GB18030 自动探测），按学号幂等去重：已存在的学号更新姓名，
     * 不产生重复记录；候选人面试状态不受导入影响。解析规则与跳过统计口径见接口文档。
     *
     * @throws IOException 文件不存在或不可读时抛出
     */
    public ImportReport importRoster(Path file) throws IOException {
        RosterParser.Parsed p = RosterParser.parse(file);
        int added = db(() -> {
            int inserted = 0;
            for (RosterParser.Entry e : p.entries()) {
                if (e.name().length() > NAME_MAX) {
                    throw new IllegalStateException("名单中含超长姓名行（>64 字），无法导入。请检查第 "
                            + "「" + e.name().substring(0, 30) + "…」行附近的源文件格式。");
                }
                if (store.upsertCandidate(e.studentNo(), e.name())) {
                    inserted++;
                }
            }
            return inserted;
        });
        int parsedLines = p.entries().size();
        return new ImportReport(parsedLines, p.skippedCount(), added,
                parsedLines - added, p.encodingName(), p.skippedSamples());
    }

    // ------------------------------------------------------------------ 查询

    /** 全部候选人，按学号升序。 */
    public List<CandidateInfo> listCandidates() {
        return db(store::listCandidates);
    }

    /**
     * 搜索候选人：关键字为纯数字时按「学号精确匹配，其次前缀匹配」（超过 10 位时取前 10 位参与匹配）；
     * 含非数字字符时按姓名模糊匹配（包含即可）。最多返回 20 条，按学号升序。
     */
    public List<CandidateInfo> searchCandidates(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            throw new IllegalArgumentException("搜索关键字不能为空。");
        }
        if (kw.chars().allMatch(Character::isDigit)) {
            final String prefix = kw.length() > 10 ? kw.substring(0, 10) : kw;
            return db(() -> {
                Optional<CandidateInfo> exact = store.findCandidate(prefix);
                if (exact.isPresent()) {
                    return List.of(exact.get());
                }
                return store.searchByNoPrefix(prefix, 20);
            });
        }
        final String nameKw = kw;
        return db(() -> store.searchByName(nameKw, 20));
    }

    /** 按学号取候选人；不存在抛 IllegalArgumentException。 */
    public CandidateInfo getCandidate(String studentNo) {
        return requireCandidate(studentNo);
    }

    /** 当前处于面试中的候选人；无则返回 null（GUI 用它做「恢复面试」入口判断）。 */
    public CandidateInfo currentInterviewing() {
        return db(store::findInterviewing).orElse(null);
    }

    /** 候选人完整评分明细（平均分/最终分动态计算，含普通评分与附加分列表）。 */
    public CandidateDetail candidateDetail(String studentNo) {
        CandidateInfo c = requireCandidate(studentNo);
        return db(() -> buildDetail(c));
    }

    // ------------------------------------------------------------------ 面试状态机

    /**
     * 开始/恢复某候选人的面试（PENDING→INTERVIEWING；INTERVIEWING 幂等恢复）。
     * 同一时刻全局最多一名 INTERVIEWING；FINISHED 不可重开（走补录/附加或先初始化）。
     */
    public CandidateInfo beginInterview(String studentNo) {
        CandidateInfo c = requireCandidate(studentNo);
        switch (c.status()) {
            case FINISHED:
                throw new IllegalStateException("候选人 " + c.name() + "（" + c.studentNo()
                        + "）已结束面试，不能重新面试。如需修改请使用「补录 / 附加」功能，"
                        + "或先执行「初始化系统」。");
            case INTERVIEWING:
                return c;
            default:
                db(() -> {
                    Optional<CandidateInfo> busy = store.findInterviewing();
                    if (busy.isPresent() && !busy.get().studentNo().equals(studentNo)) {
                        throw new IllegalStateException("同一时刻只能面试一位候选人：当前 "
                                + busy.get().name() + "（" + busy.get().studentNo()
                                + "）正在面试中。请先结束其面试，或在主菜单执行初始化。");
                    }
                    store.updateStatus(studentNo, CandidateStatus.INTERVIEWING, null);
                    return null;
                });
                return requireCandidate(studentNo);
        }
    }

    /** 面试打分：为面试中的候选人录入一条普通评分（即时入库），并记入撤销日志。 */
    public ScoreItem addInterviewScore(String studentNo, BigDecimal value) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() != CandidateStatus.INTERVIEWING) {
            throw new IllegalStateException("候选人 " + c.name() + " 当前未处于面试中"
                    + (c.status() == CandidateStatus.FINISHED ? "（已结束面试，请使用补录）。"
                    : "（请先开始面试）。"));
        }
        BigDecimal v = normalizeValue(value, "分值");
        return db(() -> store.tx(() -> {
            ScoreItem item = store.insertScore(studentNo, v, LocalDateTime.now());
            store.insertOpLog(studentNo, "ADD_SCORE", item.id(), v, null, item.addedAt());
            return item;
        }));
    }

    /**
     * 删除面试中候选人「最近一条」普通评分（误输入处理），删除动作本身记入日志、可被还原。
     */
    public ScoreItem deleteLastInterviewScore(String studentNo) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() != CandidateStatus.INTERVIEWING) {
            throw new IllegalStateException("只能删除「面试中」候选人的评分（当前候选人未处于面试中）。");
        }
        return db(() -> store.tx(() -> {
            Optional<ScoreItem> last = store.lastScore(studentNo);
            if (last.isEmpty()) {
                throw new IllegalStateException("该候选人还没有评分，没有可删除的记录。");
            }
            ScoreItem item = last.get();
            store.deleteScoreByIdAndNo(studentNo, item.id());
            store.insertOpLog(studentNo, "DEL_SCORE", item.id(), item.value(), null, item.addedAt());
            return item;
        }));
    }

    /**
     * 结束评分并计算最终分。普通评分 ≥3 条时直接完成（去极值平均）；
     * 1～2 条时先返回 belowThree=true 的预览（completed=false），GUI 二次确认后以
     * {@code confirmBelowThree=true} 再次调用即按普通平均完成；0 条不允许结束。
     */
    public FinishResult finishInterview(String studentNo, boolean confirmBelowThree) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() != CandidateStatus.INTERVIEWING) {
            throw new IllegalStateException("候选人 " + c.name() + " 当前不在面试中，无法结束评分。");
        }
        return db(() -> {
            CandidateDetail d = buildDetail(c);
            if (d.scores().isEmpty()) {
                throw new IllegalStateException("该候选人还没有任何评分，无法结束面试。请先至少录入 1 条评分。");
            }
            boolean belowThree = d.scores().size() < 3;
            BigDecimal finalScore = d.average().add(d.bonusTotal()).setScale(2, RoundingMode.HALF_UP);
            if (belowThree && !confirmBelowThree) {
                return new FinishResult(false, true, d.average(), d.avgMethod(), d.bonusTotal(), null,
                        "该候选人目前仅有 " + d.scores().size() + " 条普通评分，不足 3 条，无法去掉最高分和最低分。"
                                + "确认结束的话，最终分将按普通平均 "
                                + d.average().toPlainString() + " 计算（含附加分后为 "
                                + finalScore.toPlainString() + "）。是否确认结束面试？");
            }
            store.updateStatus(studentNo, CandidateStatus.FINISHED, LocalDateTime.now());
            CandidateInfo finished = store.findCandidate(studentNo).orElse(c);
            CandidateDetail after = buildDetail(finished);
            BigDecimal fin = after.average().add(after.bonusTotal()).setScale(2, RoundingMode.HALF_UP);
            return new FinishResult(true, belowThree, after.average(), after.avgMethod(),
                    after.bonusTotal(), fin, "面试已完成。");
        });
    }

    // ------------------------------------------------------------------ 补录 / 附加

    /**
     * 分数补录：仅允许「已结束面试（FINISHED）」候选人，补录后最终分自动按最新明细重算
     * （重新去极值；若补录后仍不足 3 条则按普通平均口径）。补录同样记入撤销日志。
     */
    public ScoreItem addMakeupScore(String studentNo, BigDecimal value) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() != CandidateStatus.FINISHED) {
            throw new IllegalStateException("补录仅适用于已结束面试的候选人。"
                    + (c.status() == CandidateStatus.PENDING ? "该候选人尚未开始面试。"
                    : "该候选人正在面试中，请直接在面试打分中添加评分。"));
        }
        BigDecimal v = normalizeValue(value, "分值");
        return db(() -> store.tx(() -> {
            ScoreItem item = store.insertScore(studentNo, v, LocalDateTime.now());
            store.insertOpLog(studentNo, "ADD_SCORE", item.id(), v, null, item.addedAt());
            return item;
        }));
    }

    /**
     * 分数附加：为候选人（任意状态）添加一笔附加分（如才艺加分），必须填写原因。
     * 附加分直接计入最终分、不参与去极值平均，单独列示；对已结束面试者立即反映到最终分。
     */
    public BonusItem addBonus(String studentNo, BigDecimal amount, String reason) {
        CandidateInfo c = requireCandidate(studentNo);
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new IllegalArgumentException("请填写附加分原因（如：才艺加分）。");
        }
        if (r.length() > 200) {
            throw new IllegalArgumentException("附加分原因不能超过 200 字。");
        }
        BigDecimal v = normalizeValue(amount, "附加分值");
        return db(() -> store.tx(() -> {
            BonusItem item = store.insertBonus(studentNo, v, r, LocalDateTime.now());
            store.insertOpLog(studentNo, "ADD_BONUS", item.id(), v, r, item.addedAt());
            return item;
        }));
    }

    // ------------------------------------------------------------------ 撤销

    /** 最近一条评分操作（用于 GUI 展示「将撤销什么」）；无操作时返回 null。 */
    public OpLogEntry latestOperation() {
        return db(() -> store.lastOpLog().map(this::toOpLogEntry).orElse(null));
    }

    /**
     * 撤销最近一次修改评分的操作（LIFO）并重算该候选人最终分。支持深度：直到日志起点
     * （初始化会清空日志，因此不能跨初始化撤销）。约束：不会撤销到使「已结束面试」的
     * 候选人普通评分数降为 0（此时拒绝并给出提示）。无操作日志时抛 IllegalStateException。
     */
    public UndoResult undoLatest() {
        return db(() -> store.tx(() -> {
            Store.LogRow log = store.lastOpLog().orElseThrow(
                    () -> new IllegalStateException("没有任何可撤销的评分操作。"));
            CandidateInfo c = requireCandidate(log.studentNo());
            switch (log.opType()) {
                case "ADD_SCORE": {
                    Optional<ScoreItem> exists = store.findScoreByIdAndNo(c.studentNo(), log.refId());
                    if (exists.isEmpty()) {
                        throw new IllegalStateException("操作日志与评分数据不一致（评分记录缺失），无法撤销。");
                    }
                    int remaining = store.listScores(c.studentNo()).size() - 1;
                    if (c.status() == CandidateStatus.FINISHED && remaining <= 0) {
                        throw new IllegalStateException("不能撤销该操作：候选人 " + c.name()
                                + " 已结束面试且只剩这一条评分，撤销后将无法计算最终分。"
                                + "可改用「补录/附加」修正，或执行初始化后重新面试。");
                    }
                    store.deleteScoreByIdAndNo(c.studentNo(), log.refId());
                    store.deleteOpLogBySeq(log.seq());
                    return undoDone(log, c, "已撤销「添加普通评分 " + fmt2(log.value()) + "」。");
                }
                case "DEL_SCORE": {
                    LocalDateTime at = log.occurredAt() == null ? LocalDateTime.now() : log.occurredAt();
                    store.restoreScore(log.refId(), c.studentNo(), log.value(), at);
                    store.deleteOpLogBySeq(log.seq());
                    return undoDone(log, c, "已撤销「删除普通评分 " + fmt2(log.value()) + "」，评分已恢复。");
                }
                case "ADD_BONUS": {
                    store.deleteBonusByIdAndNo(c.studentNo(), log.refId());
                    store.deleteOpLogBySeq(log.seq());
                    return undoDone(log, c, "已撤销「添加附加分 " + fmt2(log.value()) + "（" + log.reason() + "）」。");
                }
                default:
                    throw new IllegalStateException("未知操作日志类型：" + log.opType() + "，无法撤销。");
            }
        }));
    }

    // ------------------------------------------------------------------ 排名 / 导出

    /**
     * 生成排名：仅包含已结束面试（FINISHED）的候选人；按最终分降序，
     * 同分按学号升序，名次连续编号。未结束者不占用名次（用 listCandidates 展示）。
     */
    public List<RankRow> ranking() {
        return db(() -> {
            List<CandidateInfo> finished = store.listByStatus(CandidateStatus.FINISHED);
            List<RankRow> rows = new ArrayList<>();
            for (CandidateInfo c : finished) {
                CandidateDetail d = buildDetail(c);
                BigDecimal finalScore = d.average().add(d.bonusTotal()).setScale(2, RoundingMode.HALF_UP);
                rows.add(new RankRow(0, c, d.average(), d.avgMethod(), d.bonusTotal(),
                        finalScore, d.scores().size(), c.finishedAt()));
            }
            rows.sort(Comparator
                    .comparing(RankRow::finalScore, Comparator.reverseOrder())
                    .thenComparing(r -> r.candidate().studentNo()));
            for (int i = 0; i < rows.size(); i++) {
                rows.set(i, new RankRow(i + 1, rows.get(i).candidate(), rows.get(i).average(),
                        rows.get(i).avgMethod(), rows.get(i).bonusTotal(),
                        rows.get(i).finalScore(), rows.get(i).normalScoreCount(), rows.get(i).finishedAt()));
            }
            return rows;
        });
    }

    /**
     * 导出 CSV（UTF-8 编码、带 BOM，Excel 直接打开不乱码；已做单元格转义与公式注入防护）。
     *
     * @param file              输出路径（父目录自动创建）
     * @param includeUnfinished true=已完成排前面、未完成（按学号）附在后面，分数列留空但明细保留；
     *                          false=仅导出已完成候选人
     * @return 导出的数据行数（不含表头）
     */
    public int exportCsv(Path file, boolean includeUnfinished) throws IOException {
        List<RankRow> rows = ranking();
        List<CandidateInfo> unfinished = new ArrayList<>();
        if (includeUnfinished) {
            for (CandidateInfo c : listCandidates()) {
                if (c.status() != CandidateStatus.FINISHED) {
                    unfinished.add(c);
                }
            }
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>();
        lines.add("排名,学号,姓名,状态,最终分,平均分,平均方式,附加分合计,普通评分条数,普通评分明细,附加分明细,面试完成时间");
        for (RankRow r : rows) {
            CandidateDetail d = candidateDetail(r.candidate().studentNo());
            lines.add(String.join(",", esc(String.valueOf(r.rank())), esc(r.candidate().studentNo()),
                    esc(r.candidate().name()), esc(r.candidate().status().label()),
                    esc(fmt2(r.finalScore())), esc(fmt2(r.average())), esc(r.avgMethod().label()),
                    esc(fmt2(r.bonusTotal())), esc(String.valueOf(r.normalScoreCount())),
                    esc(scoresCsv(d)), esc(bonusesCsv(d)), esc(fmtTime(r.finishedAt()))));
        }
        if (includeUnfinished) {
            for (CandidateInfo c : unfinished) {
                CandidateDetail d = candidateDetail(c.studentNo());
                lines.add(String.join(",", esc(""), esc(c.studentNo()), esc(c.name()),
                        esc(c.status().label()), esc(""), esc(""), esc(""), esc(""),
                        esc(String.valueOf(d.scores().size())), esc(scoresCsv(d)), esc(bonusesCsv(d)), esc("")));
            }
        }
        StringBuilder sb = new StringBuilder("\uFEFF");
        for (String line : lines) {
            sb.append(line).append("\r\n");
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return rows.size() + unfinished.size();
    }

    // ------------------------------------------------------------------ 统计 / 初始化

    /** 系统状态统计。 */
    public SystemStats stats() {
        return db(store::stats);
    }

    /**
     * 结束当前面试并初始化系统：清空全部普通评分、附加分、操作日志，
     * 所有候选人回到「未面试」；候选人名单保留。（调用方需自行完成二次确认与备份提示。）
     */
    public void resetAll() {
        db(() -> {
            store.resetAll();
            return null;
        });
    }

    // ------------------------------------------------------------------ 内部工具

    private CandidateDetail buildDetail(CandidateInfo c) throws SQLException {
        List<ScoreItem> scores = store.listScores(c.studentNo());
        List<BonusItem> bonuses = store.listBonuses(c.studentNo());
        BigDecimal bonusTotal = BigDecimal.ZERO;
        for (BonusItem b : bonuses) {
            bonusTotal = bonusTotal.add(b.amount());
        }
        BigDecimal average = null;
        AvgMethod method = AvgMethod.PLAIN;
        if (!scores.isEmpty()) {
            if (scores.size() >= 3) {
                List<BigDecimal> sorted = scores.stream()
                        .map(ScoreItem::value).sorted().toList();
                BigDecimal sum = BigDecimal.ZERO;
                for (int i = 1; i < sorted.size() - 1; i++) {
                    sum = sum.add(sorted.get(i));
                }
                average = sum.divide(BigDecimal.valueOf(sorted.size() - 2), 6, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP);
                method = AvgMethod.TRIMMED;
            } else {
                BigDecimal sum = BigDecimal.ZERO;
                for (ScoreItem s : scores) {
                    sum = sum.add(s.value());
                }
                average = sum.divide(BigDecimal.valueOf(scores.size()), 6, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal finalScore = null;
        if (c.status() == CandidateStatus.FINISHED && average != null) {
            finalScore = average.add(bonusTotal).setScale(2, RoundingMode.HALF_UP);
        }
        return new CandidateDetail(c, scores, bonuses, average, method, bonusTotal, finalScore);
    }

    private CandidateInfo requireCandidate(String studentNo) {
        String no = studentNo == null ? "" : studentNo.trim();
        if (!RosterParser.STUDENT_NO.matcher(no).matches()) {
            throw new IllegalArgumentException("学号应为恰好 10 位数字，收到：" + no);
        }
        return db(() -> store.findCandidate(no).orElseThrow(
                () -> new IllegalArgumentException("名单中不存在学号 " + no + " 的候选人，请先导入名单。")));
    }

    private static BigDecimal normalizeValue(BigDecimal v, String what) {
        if (v == null) {
            throw new IllegalArgumentException(what + "不能为空。");
        }
        BigDecimal t = v.stripTrailingZeros();
        if (t.scale() > 2) {
            throw new IllegalArgumentException(what + "最多支持两位小数。");
        }
        if (t.compareTo(MIN_VALUE) < 0 || t.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException(what + "需在 0～100 之间。");
        }
        return t.setScale(2, RoundingMode.UNNECESSARY);
    }

    private OpLogEntry toOpLogEntry(Store.LogRow log) {
        String opText;
        switch (log.opType()) {
            case "ADD_SCORE":
                opText = "给 " + log.studentName() + "（" + log.studentNo() + "）添加普通评分 " + fmt2(log.value());
                break;
            case "DEL_SCORE":
                opText = "删除 " + log.studentName() + "（" + log.studentNo() + "）的普通评分 " + fmt2(log.value()) + "（误输入）";
                break;
            case "ADD_BONUS":
                opText = "给 " + log.studentName() + "（" + log.studentNo() + "）添加附加分 "
                        + fmt2(log.value()) + "（" + log.reason() + "）";
                break;
            default:
                opText = "未知操作（" + log.opType() + "）";
        }
        return new OpLogEntry(log.seq(), log.studentNo(), log.studentName(), log.opType(),
                log.value(), log.reason(), opText, log.time());
    }

    private UndoResult undoDone(Store.LogRow log, CandidateInfo c, String description) throws SQLException {
        CandidateDetail d = buildDetail(c);
        return new UndoResult(log.studentNo(), log.studentName(), description,
                c.status() == CandidateStatus.FINISHED, d.finalScore(), d.scores().size());
    }

    private static String scoresCsv(CandidateDetail d) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < d.scores().size(); i++) {
            parts.add(d.scores().get(i).value().toPlainString());
        }
        return String.join(";", parts);
    }

    private static String bonusesCsv(CandidateDetail d) {
        List<String> parts = new ArrayList<>();
        for (BonusItem b : d.bonuses()) {
            parts.add("+" + b.amount().toPlainString() + "(" + b.reason() + ")");
        }
        return String.join(";", parts);
    }

    private static String esc(String cell) {
        if (cell == null) {
            return "";
        }
        String safe = cell;
        if (!safe.isEmpty() && (safe.charAt(0) == '=' || safe.charAt(0) == '+' || safe.charAt(0) == '-'
                || safe.charAt(0) == '@' || safe.charAt(0) == '\t' || safe.charAt(0) == '\r')) {
            safe = "'" + safe;
        }
        if (safe.contains("\"") || safe.contains(",") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String fmt2(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fmtTime(LocalDateTime t) {
        return t == null ? "" : TIME_FMT.format(t);
    }

    // ------------------------------------------------------------------ 数据库异常包装

    private interface ThrowingSupplier<T> {
        T get() throws SQLException;
    }

    private <T> T db(ThrowingSupplier<T> action) {
        try {
            return action.get();
        } catch (SQLException e) {
            throw new IllegalStateException("数据库操作失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        store.close();
    }
}
