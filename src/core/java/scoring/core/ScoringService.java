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
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 学生组织面试评分系统 —— core 层门面服务（GUI 直接复用的入口类；二期：四维评分/名单 CRUD/彻底重置）。
 *
 * <p>二期评分模型：一条评分记录 = 四维（r 责任心 / t 时间管理能力 / s 学生工作能力 / f 部门契合度），
 * 每维 0～25、最多两位小数。平均口径：每维度独立计算——n≥3 去该维度一个最高/最低后平均；
 * n=1～2 普通平均（结束面试时二次确认）；n=0 拒绝结束。最终分 = 四维平均之和（≤100）+ Σ附加分
 * （单笔 ≤10、可多笔、无总上限）。最终分不落库，全部动态计算。
 *
 * <p>本类不依赖任何控制台/UI API；面向单进程单实例使用。
 */
public final class ScoringService implements AutoCloseable {

    /** 分值域：维度 0～25；附加分单笔 0～10；均最多两位小数。 */
    public static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    public static final BigDecimal DIM_MAX = new BigDecimal("25");
    public static final BigDecimal BONUS_MAX = new BigDecimal("10");
    /** 兼容旧名（一期曾表示总分上限 100）：现指四维单维上限 25。 */
    public static final BigDecimal MAX_VALUE = DIM_MAX;

    /** 四维中文标签（展示/消息用，顺序与排序优先级一致）。 */
    public static final String[] DIM_LABELS = {"责任心", "时间管理能力", "学生工作能力", "部门契合度"};

    private static final int NAME_MAX = 64;

    private final Store store;

    /** @param jdbcUrl H2 JDBC URL，如 {@code jdbc:h2:file:data/scoring}（父目录需已存在）。 */
    public ScoringService(String jdbcUrl) {
        this.store = new Store(jdbcUrl);
    }

    /** 本次打开是否发生过“一期旧库自动迁移”；无则返回 null。 */
    public String migrationNotice() {
        return store.migrationNotice();
    }

    // ------------------------------------------------------------------ 名单导入

    /**
     * 导入名单：.xlsx/.xls（按文件头魔数识别）走 Excel 行提取（Tab 拼接后复用统一解析管线）；
     * 其余走文本管线（编码探测不变）。按学号幂等去重，候选人面试状态不受导入影响。
     *
     * @throws IOException 文件不存在或不可读时抛出
     */
    public ImportReport importRoster(Path file) throws IOException {
        RosterParser.Parsed p = isExcelFile(file) ? ExcelRosterReader.read(file) : RosterParser.parse(file);
        int added = db(() -> {
            int inserted = 0;
            for (RosterParser.Entry e : p.entries()) {
                if (e.name().length() > NAME_MAX) {
                    throw new IllegalStateException("名单中含超长姓名行（>64 字），无法导入。请检查 "
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

    /** 魔数识别：ZIP(PK..)=xlsx；OLE(D0CF11E0..)=xls；其余视为文本。 */
    private static boolean isExcelFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(8);
            if (head.length >= 2 && head[0] == 'P' && head[1] == 'K') {
                return true;
            }
            return head.length >= 4 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF
                    && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0;
        }
    }

    // ------------------------------------------------------------------ 查询

    /** 全部候选人，按学号升序。 */
    public List<CandidateInfo> listCandidates() {
        return db(store::listCandidates);
    }

    /**
     * 搜索候选人：纯数字→学号精确匹配，其次前缀匹配（超过 10 位取前 10 位）；
     * 其它→姓名包含匹配；≤20 条。
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

    /** 当前处于面试中的候选人；无则返回 null。 */
    public CandidateInfo currentInterviewing() {
        return db(store::findInterviewing).orElse(null);
    }

    /** 候选人完整明细（四维平均/合计/最终分动态计算，含四维评分与附加分列表）。 */
    public CandidateDetail candidateDetail(String studentNo) {
        CandidateInfo c = requireCandidate(studentNo);
        return db(() -> buildDetail(c));
    }

    // ------------------------------------------------------------------ 名单管理（手动 CRUD）

    /** 手动新增候选人。姓名须为汉字链（≥2 字，可含 · 连接）；学号恰好 10 位数字且未被占用。 */
    public CandidateInfo addCandidate(String name, String studentNo) {
        String nm = name == null ? "" : name.trim();
        if (!RosterParser.isValidName(nm)) {
            throw new IllegalArgumentException("姓名应为 2~64 个汉字（少数民族姓名请用 · 连接，如 麦麦提·吐尔逊）。");
        }
        String no = requireNoFormat(studentNo);
        db(() -> {
            if (store.findCandidate(no).isPresent()) {
                throw new IllegalArgumentException("学号 " + no + " 已存在于名单中，不能重复新增。");
            }
            store.insertCandidate(no, nm);
            return null;
        });
        return getCandidate(no);
    }

    /** 手动改名（任意状态允许）。 */
    public CandidateInfo updateCandidateName(String studentNo, String newName) {
        CandidateInfo c = requireCandidate(studentNo);
        String nm = newName == null ? "" : newName.trim();
        if (!RosterParser.isValidName(nm)) {
            throw new IllegalArgumentException("姓名应为 2~64 个汉字（少数民族姓名请用 · 连接）。");
        }
        db(() -> {
            store.updateCandidateName(c.studentNo(), nm);
            return null;
        });
        return getCandidate(c.studentNo());
    }

    /**
     * 改学号：级联迁移该候选人的评分/附加分/操作日志到新学号。
     * 面试中（INTERVIEWING）禁止；新旧号冲突拒绝。
     */
    public CandidateInfo updateCandidateStudentNo(String oldNo, String newNo) {
        CandidateInfo c = requireCandidate(oldNo);
        if (c.status() == CandidateStatus.INTERVIEWING) {
            throw new IllegalStateException("该候选人正在面试中，禁止改学号。请先结束其面试（或初始化）再操作。");
        }
        String to = requireNoFormat(newNo);
        if (to.equals(c.studentNo())) {
            return c;
        }
        db(() -> {
            if (store.findCandidate(to).isPresent()) {
                throw new IllegalArgumentException("新学号 " + to + " 已被占用，无法改号。");
            }
            store.moveCandidateStudentNo(c.studentNo(), to);
            return null;
        });
        return getCandidate(to);
    }

    /**
     * 删除候选人：级联删除其全部四维评分/附加分/操作日志。
     * 面试中（INTERVIEWING）禁止；FINISHED 允许（调用方负责二次确认与风险提示）。
     * 被删学号之后可被重新添加。
     */
    public void deleteCandidate(String studentNo) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() == CandidateStatus.INTERVIEWING) {
            throw new IllegalStateException("该候选人正在面试中，禁止删除。请先结束其面试（或初始化）再操作。");
        }
        db(() -> {
            store.deleteCandidateCascade(c.studentNo());
            return null;
        });
    }

    // ------------------------------------------------------------------ 面试状态机

    /** 开始/恢复面试（PENDING→INTERVIEWING；INTERVIEWING 幂等恢复；全局唯一；FINISHED 不可重开）。 */
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

    /**
     * 面试打分：录入一条完整四维评分（r/t/s/f 各 0～25，两位小数），即时入库并记撤销日志。
     */
    public ScoreItem addInterviewScore(String studentNo, BigDecimal r, BigDecimal t, BigDecimal s, BigDecimal f) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() != CandidateStatus.INTERVIEWING) {
            throw new IllegalStateException("候选人 " + c.name() + " 当前未处于面试中"
                    + (c.status() == CandidateStatus.FINISHED ? "（已结束面试，请使用补录）。"
                    : "（请先开始面试）。"));
        }
        final BigDecimal[] dims = normalizeDims(r, t, s, f);
        return db(() -> store.tx(() -> {
            ScoreItem item = store.insertScore(studentNo, dims[0], dims[1], dims[2], dims[3],
                    LocalDateTime.now());
            store.insertOpLog(studentNo, "ADD_SCORE", item.id(),
                    dims[0], dims[1], dims[2], dims[3], null, null, item.addedAt());
            return item;
        }));
    }

    /** 删除面试中候选人最近一条四维评分（误输入处理），删除动作记日志可还原。 */
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
            store.insertOpLog(studentNo, "DEL_SCORE", item.id(),
                    item.r(), item.t(), item.s(), item.f(), null, null, item.addedAt());
            return item;
        }));
    }

    /**
     * 结束评分。n≥3 自动完成（四维各自去极值）；n=1～2 返回 belowThree 预览需二次确认；
     * n=0 拒绝结束。
     */
    public FinishResult finishInterview(String studentNo, boolean confirmBelowThree) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() != CandidateStatus.INTERVIEWING) {
            throw new IllegalStateException("候选人 " + c.name() + " 当前不在面试中，无法结束评分。");
        }
        return db(() -> {
            CandidateDetail d = buildDetail(c);
            if (d.scores().isEmpty()) {
                throw new IllegalStateException("该候选人还没有任何四维评分记录，无法结束面试。"
                        + "请先至少录入 1 条完整评分。");
            }
            boolean belowThree = d.scores().size() < 3;
            BigDecimal finalScore = d.dimensionTotal().add(d.bonusTotal()).setScale(2, RoundingMode.HALF_UP);
            if (belowThree && !confirmBelowThree) {
                return new FinishResult(false, true, d.rAvg(), d.tAvg(), d.sAvg(), d.fAvg(),
                        d.dimensionTotal(), d.avgMethod(), d.bonusTotal(), null,
                        "该候选人目前仅有 " + d.scores().size() + " 条评分记录，不足 3 条，"
                                + "四个维度无法各自去掉最高/最低分，将按普通平均计算："
                                + "责任心 " + fmt2(d.rAvg()) + "、时间管理能力 " + fmt2(d.tAvg())
                                + "、学生工作能力 " + fmt2(d.sAvg()) + "、部门契合度 " + fmt2(d.fAvg())
                                + "，四维合计 " + fmt2(d.dimensionTotal())
                                + "（含附加分后最终分为 " + fmt2(finalScore) + "）。是否确认结束面试？");
            }
            store.updateStatus(studentNo, CandidateStatus.FINISHED, LocalDateTime.now());
            CandidateInfo finished = store.findCandidate(studentNo).orElse(c);
            CandidateDetail after = buildDetail(finished);
            BigDecimal fin = after.dimensionTotal().add(after.bonusTotal()).setScale(2, RoundingMode.HALF_UP);
            return new FinishResult(true, belowThree, after.rAvg(), after.tAvg(), after.sAvg(),
                    after.fAvg(), after.dimensionTotal(), after.avgMethod(), after.bonusTotal(),
                    fin, "面试已完成。");
        });
    }

    // ------------------------------------------------------------------ 补录 / 附加

    /** 补录：仅 FINISHED，补一条完整四维评分并自动重算。 */
    public ScoreItem addMakeupScore(String studentNo, BigDecimal r, BigDecimal t, BigDecimal s, BigDecimal f) {
        CandidateInfo c = requireCandidate(studentNo);
        if (c.status() != CandidateStatus.FINISHED) {
            throw new IllegalStateException("补录仅适用于已结束面试的候选人。"
                    + (c.status() == CandidateStatus.PENDING ? "该候选人尚未开始面试。"
                    : "该候选人正在面试中，请直接在面试打分中添加评分。"));
        }
        final BigDecimal[] dims = normalizeDims(r, t, s, f);
        return db(() -> store.tx(() -> {
            ScoreItem item = store.insertScore(studentNo, dims[0], dims[1], dims[2], dims[3],
                    LocalDateTime.now());
            store.insertOpLog(studentNo, "ADD_SCORE", item.id(),
                    dims[0], dims[1], dims[2], dims[3], null, null, item.addedAt());
            return item;
        }));
    }

    /** 附加分：任意状态候选人可加；单笔 0～10、原因必填；可多笔；直接并入最终分。 */
    public BonusItem addBonus(String studentNo, BigDecimal amount, String reason) {
        CandidateInfo c = requireCandidate(studentNo);
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new IllegalArgumentException("请填写附加分原因（如：才艺加分）。");
        }
        if (r.length() > 200) {
            throw new IllegalArgumentException("附加分原因不能超过 200 字。");
        }
        BigDecimal v = normalizeValue(amount, "附加分值", BONUS_MAX);
        return db(() -> store.tx(() -> {
            BonusItem item = store.insertBonus(studentNo, v, r, LocalDateTime.now());
            store.insertOpLog(studentNo, "ADD_BONUS", item.id(),
                    null, null, null, null, v, r, item.addedAt());
            return item;
        }));
    }

    // ------------------------------------------------------------------ 撤销

    /** 最近一条评分操作（GUI 撤销预览）；无操作返回 null。 */
    public OpLogEntry latestOperation() {
        return db(() -> store.lastOpLog().map(this::toOpLogEntry).orElse(null));
    }

    /** LIFO 撤销并重算；边界见接口文档 §8.3（FINISHED 不得撤到 0 条；不可跨初始化）。 */
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
                    return undoDone(log, c, "已撤销「添加四维评分 " + dimsText(log.r(), log.t(), log.s(), log.f()) + "」。");
                }
                case "DEL_SCORE": {
                    LocalDateTime at = log.occurredAt() == null ? LocalDateTime.now() : log.occurredAt();
                    store.restoreScore(log.refId(), c.studentNo(), log.r(), log.t(), log.s(), log.f(), at);
                    store.deleteOpLogBySeq(log.seq());
                    return undoDone(log, c, "已撤销「删除四维评分 "
                            + dimsText(log.r(), log.t(), log.s(), log.f()) + "」，评分已恢复。");
                }
                case "ADD_BONUS": {
                    store.deleteBonusByIdAndNo(c.studentNo(), log.refId());
                    store.deleteOpLogBySeq(log.seq());
                    return undoDone(log, c, "已撤销「添加附加分 " + fmt2(log.amount())
                            + "（" + log.reason() + "）」");
                }
                default:
                    throw new IllegalStateException("未知操作日志类型：" + log.opType() + "，无法撤销。");
            }
        }));
    }

    // ------------------------------------------------------------------ 排名 / 导出

    /**
     * 排名：仅 FINISHED；最终分降序 → 同分按四维平均逐维比较（责任心→时间管理→学生工作→部门契合）
     * → 学号升序；名次连续。
     */
    public List<RankRow> ranking() {
        return db(() -> {
            List<CandidateInfo> finished = store.listByStatus(CandidateStatus.FINISHED);
            List<RankRow> rows = new ArrayList<>();
            for (CandidateInfo c : finished) {
                CandidateDetail d = buildDetail(c);
                // 防御：异常/手工改库可能产生“已结束但 0 条评分记录”的候选人（正常流程与迁移均不会），
                // 此时四维合计为 null，无分可排 → 跳过（不计名次）。
                if (d.dimensionTotal() == null) {
                    continue;
                }
                BigDecimal finalScore = d.dimensionTotal().add(d.bonusTotal()).setScale(2, RoundingMode.HALF_UP);
                rows.add(new RankRow(0, c, d.rAvg(), d.tAvg(), d.sAvg(), d.fAvg(),
                        d.dimensionTotal(), d.avgMethod(), d.bonusTotal(), finalScore,
                        d.scores().size(), c.finishedAt()));
            }
            rows.sort(Comparator
                    .comparing(RankRow::finalScore, Comparator.reverseOrder())
                    .thenComparing(RankRow::rAvg, Comparator.reverseOrder())
                    .thenComparing(RankRow::tAvg, Comparator.reverseOrder())
                    .thenComparing(RankRow::sAvg, Comparator.reverseOrder())
                    .thenComparing(RankRow::fAvg, Comparator.reverseOrder())
                    .thenComparing(r -> r.candidate().studentNo()));
            List<RankRow> out = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                RankRow x = rows.get(i);
                out.add(new RankRow(i + 1, x.candidate(), x.rAvg(), x.tAvg(), x.sAvg(), x.fAvg(),
                        x.dimensionTotal(), x.avgMethod(), x.bonusTotal(), x.finalScore(),
                        x.normalScoreCount(), x.finishedAt()));
            }
            return out;
        });
    }

    /**
     * 导出 CSV（UTF-8 BOM）。二期列序：名次/学号/姓名/责任心/时间管理能力/学生工作能力/部门契合度/
     * 四维合计/平均方式/评分记录数/附加分明细(分值(理由);连接)/附加分合计/最终分。
     * 未完成行：名次与分数留空，记录数/附加明细保留（备份价值）。
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
        lines.add("排名,学号,姓名,责任心,时间管理能力,学生工作能力,部门契合度,四维合计,平均方式,评分记录数,附加分明细,附加分合计,最终分");
        for (RankRow r : rows) {
            CandidateDetail d = candidateDetail(r.candidate().studentNo());
            lines.add(String.join(",", esc(String.valueOf(r.rank())), esc(r.candidate().studentNo()),
                    esc(r.candidate().name()),
                    esc(fmt2(r.rAvg())), esc(fmt2(r.tAvg())), esc(fmt2(r.sAvg())), esc(fmt2(r.fAvg())),
                    esc(fmt2(r.dimensionTotal())), esc(r.avgMethod().label()),
                    esc(String.valueOf(r.normalScoreCount())),
                    esc(bonusesCsv(d)), esc(fmt2(r.bonusTotal())), esc(fmt2(r.finalScore()))));
        }
        if (includeUnfinished) {
            for (CandidateInfo c : unfinished) {
                CandidateDetail d = candidateDetail(c.studentNo());
                lines.add(String.join(",", esc(""), esc(c.studentNo()), esc(c.name()),
                        esc(""), esc(""), esc(""), esc(""), esc(""), esc(""),
                        esc(String.valueOf(d.scores().size())), esc(bonusesCsv(d)), esc(""), esc("")));
            }
        }
        StringBuilder sb = new StringBuilder("\uFEFF");
        for (String line : lines) {
            sb.append(line).append("\r\n");
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return rows.size() + unfinished.size();
    }

    // ------------------------------------------------------------------ 统计 / 初始化 / 彻底重置

    /** 系统状态统计。 */
    public SystemStats stats() {
        return db(store::stats);
    }

    /** 初始化：清空全部四维评分/附加分/操作日志，候选人回到未面试（名单保留）。 */
    public void resetAll() {
        db(() -> {
            store.resetAll();
            return null;
        });
    }

    /**
     * 彻底重置：删除一切数据与数据库文件（含 data/ 下导出/备份 CSV），并原地重建空库。
     * 与 {@link #resetAll()} 的区别：重置连候选人名单与库文件全删；初始化保留名单。
     * 调用方必须自行完成危险确认；返回已删除文件列表（供界面展示）。
     */
    public List<String> resetEverything() {
        return store.resetEverything();
    }

    /** 彻底重置将删除的目标文件预览（当前存在的库/伴生文件与数据目录 CSV），供确认对话框展示。 */
    public List<String> resetTargetPreview() {
        return store.resetTargets();
    }

    // ------------------------------------------------------------------ 内部工具

    /** 四维平均计算（每维独立口径）：n≥3 去极值；n=1~2 普通平均；n=0 → 各维 null、PLAIN。 */
    private CandidateDetail buildDetail(CandidateInfo c) throws SQLException {
        List<ScoreItem> scores = store.listScores(c.studentNo());
        List<BonusItem> bonuses = store.listBonuses(c.studentNo());
        BigDecimal bonusTotal = BigDecimal.ZERO;
        for (BonusItem b : bonuses) {
            bonusTotal = bonusTotal.add(b.amount());
        }
        Dims dims = dimAverages(scores);
        BigDecimal finalScore = null;
        if (c.status() == CandidateStatus.FINISHED && dims.total() != null) {
            finalScore = dims.total().add(bonusTotal).setScale(2, RoundingMode.HALF_UP);
        }
        return new CandidateDetail(c, scores, bonuses, dims.r(), dims.t(), dims.s(), dims.f(),
                dims.total(), dims.method(), bonusTotal, finalScore);
    }

    private record Dims(BigDecimal r, BigDecimal t, BigDecimal s, BigDecimal f,
                        BigDecimal total, AvgMethod method) {
    }

    /** 逐维平均：维度内排序后去一个最高/一个最低（n≥3），或普通平均（n=1~2）。 */
    private static Dims dimAverages(List<ScoreItem> scores) {
        if (scores.isEmpty()) {
            return new Dims(null, null, null, null, null, AvgMethod.PLAIN);
        }
        AvgMethod method = scores.size() >= 3 ? AvgMethod.TRIMMED : AvgMethod.PLAIN;
        BigDecimal r = dimAvg(values(scores, 0), method);
        BigDecimal t = dimAvg(values(scores, 1), method);
        BigDecimal s = dimAvg(values(scores, 2), method);
        BigDecimal f = dimAvg(values(scores, 3), method);
        BigDecimal total = r.add(t).add(s).add(f).setScale(2, RoundingMode.HALF_UP);
        return new Dims(r, t, s, f, total, method);
    }

    private static List<BigDecimal> values(List<ScoreItem> scores, int dim) {
        List<BigDecimal> out = new ArrayList<>(scores.size());
        for (ScoreItem item : scores) {
            out.add(dim == 0 ? item.r() : dim == 1 ? item.t() : dim == 2 ? item.s() : item.f());
        }
        return out;
    }

    private static BigDecimal dimAvg(List<BigDecimal> vals, AvgMethod method) {
        if (method == AvgMethod.TRIMMED) {
            List<BigDecimal> sorted = vals.stream().sorted().toList();
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 1; i < sorted.size() - 1; i++) {
                sum = sum.add(sorted.get(i));
            }
            return sum.divide(BigDecimal.valueOf(sorted.size() - 2), 6, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : vals) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(vals.size()), 6, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private CandidateInfo requireCandidate(String studentNo) {
        String no = requireNoFormat(studentNo);
        return db(() -> store.findCandidate(no).orElseThrow(
                () -> new IllegalArgumentException("名单中不存在学号 " + no + " 的候选人，请先导入名单。")));
    }

    private static String requireNoFormat(String studentNo) {
        String no = studentNo == null ? "" : studentNo.trim();
        if (!RosterParser.STUDENT_NO.matcher(no).matches()) {
            throw new IllegalArgumentException("学号应为恰好 10 位数字，收到：" + no);
        }
        return no;
    }

    private static BigDecimal[] normalizeDims(BigDecimal r, BigDecimal t, BigDecimal s, BigDecimal f) {
        return new BigDecimal[]{
                normalizeValue(r, "责任心分值", DIM_MAX),
                normalizeValue(t, "时间管理能力分值", DIM_MAX),
                normalizeValue(s, "学生工作能力分值", DIM_MAX),
                normalizeValue(f, "部门契合度分值", DIM_MAX)};
    }

    private static BigDecimal normalizeValue(BigDecimal v, String what, BigDecimal max) {
        if (v == null) {
            throw new IllegalArgumentException(what + "不能为空。");
        }
        BigDecimal t = v.stripTrailingZeros();
        if (t.scale() > 2) {
            throw new IllegalArgumentException(what + "最多支持两位小数。");
        }
        if (t.compareTo(MIN_VALUE) < 0 || t.compareTo(max) > 0) {
            throw new IllegalArgumentException(what + "需在 0～" + max.toPlainString() + " 之间。");
        }
        return t.setScale(2, RoundingMode.UNNECESSARY);
    }

    private OpLogEntry toOpLogEntry(Store.LogRow log) {
        String opText;
        switch (log.opType()) {
            case "ADD_SCORE":
                opText = "给 " + log.studentName() + "（" + log.studentNo() + "）添加四维评分 "
                        + dimsText(log.r(), log.t(), log.s(), log.f());
                break;
            case "DEL_SCORE":
                opText = "删除 " + log.studentName() + "（" + log.studentNo() + "）的四维评分 "
                        + dimsText(log.r(), log.t(), log.s(), log.f()) + "（误输入）";
                break;
            case "ADD_BONUS":
                opText = "给 " + log.studentName() + "（" + log.studentNo() + "）添加附加分 "
                        + fmt2(log.amount()) + "（" + log.reason() + "）";
                break;
            default:
                opText = "未知操作（" + log.opType() + "）";
        }
        return new OpLogEntry(log.seq(), log.studentNo(), log.studentName(), log.opType(),
                log.r(), log.t(), log.s(), log.f(), log.amount(), log.reason(), opText, log.time());
    }

    private UndoResult undoDone(Store.LogRow log, CandidateInfo c, String description) throws SQLException {
        CandidateDetail d = buildDetail(c);
        return new UndoResult(log.studentNo(), log.studentName(), description,
                c.status() == CandidateStatus.FINISHED, d.finalScore(), d.scores().size());
    }

    private static String dimsText(BigDecimal r, BigDecimal t, BigDecimal s, BigDecimal f) {
        return DIM_LABELS[0] + "=" + fmt2(r) + "," + DIM_LABELS[1] + "=" + fmt2(t)
                + "," + DIM_LABELS[2] + "=" + fmt2(s) + "," + DIM_LABELS[3] + "=" + fmt2(f);
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
