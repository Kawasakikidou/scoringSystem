package scoring.cli;

import scoring.core.ScoringService;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 中文交互式 CLI 壳层：菜单编号选择 + 明确提示，面向非技术背景面试官。
 * 本类只做「读输入/打印/调 ScoringService」，不含任何业务规则。
 */
final class CliApp {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BufferedReader in;
    private final PrintStream out;
    private final String jdbcUrl;

    CliApp(BufferedReader in, PrintStream out, String jdbcUrl) {
        this.in = in;
        this.out = out;
        this.jdbcUrl = jdbcUrl;
    }

    // ------------------------------------------------------------------ 主流程

    void runLoop() {
        try (ScoringService svc = open()) {
            out.println();
            out.println("==================================================");
            out.println("   欢迎使用「学生组织面试评分系统」CLI");
            out.println("==================================================");
            out.println("提示：名单/导出文件路径均相对“程序根目录（启动目录）”解析；");
            out.println("      默认名单文件为当前目录下的 名单.txt。");
            String notice = svc.migrationNotice();
            if (notice != null) {
                out.println("【迁移提示】" + notice);
            }
            while (true) {
                int choice = mainMenu(svc);
                if (choice == 0) {
                    break;
                }
                try {
                    switch (choice) {
                        case 1 -> menuImport(svc);
                        case 2 -> menuInterview(svc);
                        case 3 -> menuList(svc);
                        case 4 -> menuRanking(svc);
                        case 5 -> menuExport(svc);
                        case 6 -> menuMakeup(svc);
                        case 7 -> menuBonus(svc);
                        case 8 -> menuUndo(svc);
                        case 9 -> menuReset(svc);
                        case 10 -> menuManage(svc);
                        case 11 -> menuNuke(svc);
                        default -> out.println("无效选项，请重新输入。");
                    }
                } catch (EndOfInput e) {
                    throw e;
                } catch (RuntimeException e) {
                    out.println("操作未完成：" + e.getMessage());
                }
            }
            out.println("已退出系统。数据已保存在数据库文件中，下次启动可继续。");
        } catch (EndOfInput e) {
            out.println();
            out.println("输入流结束，程序退出。");
        } catch (IllegalStateException e) {
            System.err.println("系统错误：" + e.getMessage());
            System.err.println("请确认：1) lib/ 目录含 H2 驱动 jar（运行 scripts/fetch-libs.sh）；"
                    + "2) 数据库目录可写。");
        }
    }

    /** 非交互模式：导入一次并打印报告；成功返回 true，失败返回 false（供 Main 设置退出码）。 */
    boolean importOnly(String rosterPath) {
        try (ScoringService svc = open()) {
            String notice = svc.migrationNotice();
            if (notice != null) {
                System.err.println("【迁移提示】" + notice);
            }
            ImportReport r = svc.importRoster(Path.of(rosterPath));
            printImportReport(r, rosterPath);
            return true;
        } catch (EndOfInput e) {
            return false;
        } catch (IOException e) {
            System.err.println("导入失败：" + e.getMessage());
            return false;
        } catch (IllegalStateException e) {
            System.err.println("启动失败：" + e.getMessage());
            return false;
        }
    }

    private ScoringService open() {
        return new ScoringService(jdbcUrl);
    }

    // ------------------------------------------------------------------ 主菜单

    private int mainMenu(ScoringService svc) {
        SystemStats st = svc.stats();
        out.println();
        out.println("--------------------------------------------------");
        out.println("【系统状态】名单 " + st.totalCandidates() + " 人"
                + " ｜ 未面试 " + st.pending() + " ｜ 面试中 " + st.interviewing()
                + " ｜ 已结束 " + st.finished()
                + " ｜ 四维评分记录 " + st.scoreCount() + " 条 ｜ 附加分 " + st.bonusCount() + " 条");
        if (st.interviewing() > 0) {
            CandidateInfo cur = svc.currentInterviewing();
            out.println("  ※ 注意：有未完成的面试："
                    + (cur == null ? "（状态异常，可执行 9 初始化）" : cur.name() + "（" + cur.studentNo() + "）"));
        }
        out.println("--------------------------------------------------");
        out.println(" 1. 导入名单文件（txt/csv/xlsx/xls）");
        out.println(" 2. 面试打分（四维评分：责任心/时间管理/学生工作/部门契合，各 0~25）");
        out.println(" 3. 查看候选人名单与状态");
        out.println(" 4. 查看排名与评分明细");
        out.println(" 5. 导出 CSV（Excel 可打开）");
        out.println(" 6. 分数补录（仅限已结束面试，补一条四维评分）");
        out.println(" 7. 分数附加（单笔 0~10，需填原因，可多笔）");
        out.println(" 8. 撤销最近一次评分操作");
        out.println(" 9. 初始化系统（清空全部评分，保留名单）");
        out.println("10. 名单管理（新增/改名/改学号/删除）");
        out.println("11. 彻底重置（危险：删除一切数据与数据库文件）");
        out.println(" 0. 退出");
        return askInt("请输入功能编号", 0, 11);
    }

    // ------------------------------------------------------------------ 1 导入

    private void menuImport(ScoringService svc) {
        String input = ask("输入名单文件路径（直接回车 = 默认名单文件 " + Main.DEFAULT_ROSTER + "；q 取消）");
        if ("q".equalsIgnoreCase(input)) {
            return;
        }
        String path = input.isEmpty() ? Main.DEFAULT_ROSTER : input;
        try {
            ImportReport r = svc.importRoster(Path.of(path));
            printImportReport(r, path);
        } catch (IOException e) {
            out.println("导入失败：无法读取文件「" + path + "」：" + e.getMessage());
        }
    }

    private void printImportReport(ImportReport r, String path) {
        out.println("导入完成：「" + path + "」（编码识别：" + r.encoding() + "）");
        out.println("  成功解析 " + r.parsedLines() + " 行（新增 " + r.added()
                + " 人、更新/去重 " + r.updated() + " 行）");
        if (r.parsedLines() == 0) {
            out.println("  跳过 " + r.skippedLines() + " 行（整个文件没有一行可解析为候选人）");
        } else {
            out.println("  跳过 " + r.skippedLines() + " 行（题头/表头与空行不计入）");
        }
        for (String sample : r.skippedSamples()) {
            out.println("    跳过示例：" + sample);
        }
        if (r.skippedLines() > r.skippedSamples().size()) {
            out.println("    ……（更多跳过行未显示，请检查源文件格式）");
        }
    }

    // ------------------------------------------------------------------ 2 面试打分

    private void menuInterview(ScoringService svc) {
        CandidateInfo cur = svc.currentInterviewing();
        if (cur != null) {
            if (!askYesNo("检测到未完成的面试：" + cur.name() + "（" + cur.studentNo()
                    + "）。是否继续该候选人的面试？（y=继续 / n=返回主菜单）")) {
                out.println("已返回主菜单。注意：同一时刻只能面试一位候选人，请先结束或完成当前面试。");
                return;
            }
            interviewSession(svc, cur);
            return;
        }
        CandidateInfo picked = pickCandidate(svc, "面试打分——搜索候选人");
        if (picked == null) {
            return;
        }
        CandidateInfo begin = svc.beginInterview(picked.studentNo());
        out.println("已开始面试：" + begin.name() + "（" + begin.studentNo() + "）。每输入一条分数都会立即保存。");
        interviewSession(svc, begin);
    }

    /** 面试交互循环；返回 true 表示面试已结束（回主菜单）。 */
    private void interviewSession(ScoringService svc, CandidateInfo cand) {
        while (true) {
            CandidateDetail d = svc.candidateDetail(cand.studentNo());
            out.println();
            out.println("====== 正在面试：" + d.candidate().name() + "（" + d.candidate().studentNo()
                    + "）  已录四维评分记录 " + d.scores().size() + " 条 ======");
            out.println(" 1. 添加评分（依次输入四个维度：责任心/时间管理/学生工作/部门契合，各 0~25）");
            out.println(" 2. 删除最近一条评分（误输入时使用）");
            out.println(" 3. 结束评分并计算最终分");
            out.println(" 0. 暂存退出（保持“面试中”状态，下次可继续）");
            int c = askInt("请选择", 0, 3);
            switch (c) {
                case 0 -> {
                    out.println("已暂存。该候选人仍处于“面试中”，下次选择「面试打分」可继续。");
                    return;
                }
                case 1 -> {
                    BigDecimal[] dims = askFourDims("录入一条四维评分");
                    if (dims == null) {
                        continue;
                    }
                    svc.addInterviewScore(cand.studentNo(), dims[0], dims[1], dims[2], dims[3]);
                    CandidateDetail d2 = svc.candidateDetail(cand.studentNo());
                    out.println("已保存第 " + d2.scores().size() + " 条四维评分：" + dimsText(dims)
                            + "。可继续添加，或选择 3 结束评分。");
                }
                case 2 -> {
                    if (askYesNo("确认删除该候选人最近一条评分？（可随后用主菜单 8 撤销删除）", false)) {
                        ScoreItem removed = svc.deleteLastInterviewScore(cand.studentNo());
                        out.println("已删除评分：" + dimsText(removed.r(), removed.t(), removed.s(), removed.f())
                                + "。");
                    }
                }
                case 3 -> {
                    try {
                        if (tryFinish(svc, cand)) {
                            return;
                        }
                    } catch (IllegalStateException e) {
                        // 如“还没有任何评分”：提示后留在面试循环，而不是被踢回主菜单
                        out.println(e.getMessage());
                    }
                }
                default -> {
                }
            }
        }
    }

    /** 依次询问四个维度分值（各 0~25、两位小数，由 core 最终校验）；中途 q 取消返回 null。 */
    private BigDecimal[] askFourDims(String title) {
        out.println("------ " + title + " ------");
        BigDecimal[] dims = new BigDecimal[ScoringService.DIM_LABELS.length];
        for (int i = 0; i < dims.length; i++) {
            BigDecimal v = askScore("请输入「" + ScoringService.DIM_LABELS[i]
                    + "」分值（0~25，最多两位小数；q 取消本条）");
            if (v == null) {
                out.println("已取消本条评分输入（未保存任何维度）。");
                return null;
            }
            dims[i] = v;
        }
        return dims;
    }

    private static String dimsText(BigDecimal... dims) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ScoringService.DIM_LABELS.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(ScoringService.DIM_LABELS[i]).append("=").append(fmt2(dims[i]));
        }
        return sb.toString();
    }

    /** 结束评分：≥3 条直接完成；1~2 条提示后二次确认。返回是否已结束面试。 */
    private boolean tryFinish(ScoringService svc, CandidateInfo cand) {
        FinishResult first = svc.finishInterview(cand.studentNo(), false);
        if (first.completed()) {
            printFinish(first);
            return true;
        }
        out.println(first.message());
        if (askYesNo("是否确认结束面试？（y=确认，按普通平均计算 / n=继续添加评分）", false)) {
            FinishResult done = svc.finishInterview(cand.studentNo(), true);
            printFinish(done);
            return true;
        }
        out.println("未结束面试，可继续添加评分。");
        return false;
    }

    private void printFinish(FinishResult r) {
        out.println("面试已完成。");
        out.println("  四维平均：责任心 " + fmt2(r.rAvg()) + " ｜ 时间管理能力 " + fmt2(r.tAvg())
                + " ｜ 学生工作能力 " + fmt2(r.sAvg()) + " ｜ 部门契合度 " + fmt2(r.fAvg()));
        out.println("  四维合计：" + fmt2(r.dimensionTotal()) + "（" + r.avgMethod().label()
                + (r.belowThree() ? "，评分记录不足 3 条经确认" : "") + "）");
        out.println("  附加分合计：" + fmt2(r.bonusTotal()));
        out.println("  最终分：" + fmt2(r.finalScore()) + "（基础四维 " + fmt2(r.dimensionTotal())
                + " + 附加分 " + fmt2(r.bonusTotal()) + "）");
    }

    // ------------------------------------------------------------------ 3 查看名单

    private void menuList(ScoringService svc) {
        String kw = ask("输入关键字过滤（回车 = 显示全部；q 返回）");
        if ("q".equalsIgnoreCase(kw)) {
            return;
        }
        String filter = kw.trim();
        List<CandidateInfo> all = svc.listCandidates();
        int shown = 0;
        out.println();
        out.println("学号        姓名                 状态          完成时间");
        for (CandidateInfo c : all) {
            if (!filter.isEmpty() && !c.name().contains(filter) && !c.studentNo().startsWith(filter)) {
                continue;
            }
            shown++;
            out.println(pad(c.studentNo(), 12) + pad(c.name(), 20) + pad(c.status().label(), 14)
                    + (c.finishedAt() == null ? "" : DISPLAY_TS.format(c.finishedAt())));
        }
        if (shown == 0) {
            out.println("（没有符合条件的候选人）");
        }
        out.println("共显示 " + shown + " 人（总数 " + all.size() + " 人）。");
    }

    // ------------------------------------------------------------------ 4 排名

    private void menuRanking(ScoringService svc) {
        List<RankRow> rows = svc.ranking();
        List<CandidateInfo> all = svc.listCandidates();
        int unfinished = all.size() - rows.size();
        if (rows.isEmpty()) {
            out.println("还没有已结束面试的候选人，暂无法生成排名。");
        } else {
            out.println();
            out.println("排名  学号        姓名               最终分  四维合计 责任心  时间管理 学生工作 部门契合 平均方式  附加合计  记录数");
            for (RankRow r : rows) {
                // 列与列之间用单个空格显式分隔（各列先按显示宽度补齐，避免 CJK 列粘连）
                out.println(String.join(" ",
                        padLeft(String.valueOf(r.rank()), 5),
                        pad(r.candidate().studentNo(), 12),
                        pad(r.candidate().name(), 18),
                        padLeft(fmt2(r.finalScore()), 8),
                        padLeft(fmt2(r.dimensionTotal()), 8),
                        padLeft(fmt2(r.rAvg()), 8),
                        padLeft(fmt2(r.tAvg()), 9),
                        padLeft(fmt2(r.sAvg()), 9),
                        padLeft(fmt2(r.fAvg()), 8),
                        pad(r.avgMethod().label(), 10),
                        padLeft(fmt2(r.bonusTotal()), 10),
                        padLeft(String.valueOf(r.normalScoreCount()), 8)));
            }
            out.println("排序规则：最终分降序 → 同分比较四维平均（责任心→时间管理能力→学生工作能力→部门契合度）"
                    + "→ 学号升序。");
        }
        if (unfinished > 0) {
            out.println("另有 " + unfinished + " 人尚未完成面试，不参与排名。");
        }
        while (rows.size() > 0) {
            String no = ask("输入学号查看该候选人的评分明细（回车 / q / 0 返回主菜单）");
            if (no.isEmpty() || "q".equalsIgnoreCase(no) || "0".equals(no)) {
                return;
            }
            boolean found = false;
            for (RankRow r : rows) {
                if (r.candidate().studentNo().equals(no)) {
                    printDetail(svc.candidateDetail(no));
                    found = true;
                    break;
                }
            }
            if (!found) {
                out.println("该学号不在已完成名单中（未完成者请在主菜单 3 查看列表后，"
                        + "或在面试流程中查看当前明细）。");
            }
        }
    }

    // ------------------------------------------------------------------ 5 导出 CSV

    private void menuExport(ScoringService svc) {
        String scope;
        while (true) {
            scope = ask("导出范围：1=仅已完成（回车默认） 2=全部（含未完成，适合备份）");
            if ("q".equalsIgnoreCase(scope)) {
                return;
            }
            String s = scope.trim();
            if (s.isEmpty() || "1".equals(s) || "2".equals(s)) {
                break;
            }
            out.println("请输入 1 或 2（直接回车 = 1）。");
        }
        boolean includeAll = "2".equals(scope.trim());
        String defaultPath = "data/导出_" + LocalDateTime.now().format(FILE_TS) + ".csv";
        String input = ask("输出文件路径（回车 = 默认 " + defaultPath + "；q 取消）");
        if ("q".equalsIgnoreCase(input)) {
            return;
        }
        Path target = Path.of(input.isEmpty() ? defaultPath : input);
        try {
            int n = svc.exportCsv(target, includeAll);
            out.println("已导出 " + n + " 行数据 → " + target.toAbsolutePath());
            out.println("文件为 UTF-8 带 BOM 编码，用 Excel 打开不会乱码。");
        } catch (IOException e) {
            out.println("导出失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 6 补录 / 7 附加

    private void menuMakeup(ScoringService svc) {
        CandidateInfo picked = pickCandidate(svc, "分数补录——搜索候选人（仅限已结束面试者）");
        if (picked == null) {
            return;
        }
        CandidateInfo c = svc.getCandidate(picked.studentNo());
        if (c.status() != CandidateStatus.FINISHED) {
            out.println("不能补录：该候选人当前为「" + c.status().label() + "」。"
                    + (c.status() == CandidateStatus.PENDING ? "请先完成面试。" : "请在面试流程中直接添加评分。"));
            return;
        }
        while (true) {
            BigDecimal[] dims = askFourDims("分数补录——为 " + c.name() + "（" + c.studentNo()
                    + "）补录一条完整四维评分");
            if (dims == null) {
                return;
            }
            svc.addMakeupScore(c.studentNo(), dims[0], dims[1], dims[2], dims[3]);
            CandidateDetail d = svc.candidateDetail(c.studentNo());
            out.println("补录成功：现共 " + d.scores().size() + " 条四维评分记录。");
            out.println("  四维平均：责任心 " + fmt2(d.rAvg()) + " ｜ 时间管理能力 " + fmt2(d.tAvg())
                    + " ｜ 学生工作能力 " + fmt2(d.sAvg()) + " ｜ 部门契合度 " + fmt2(d.fAvg()));
            out.println("  四维合计 " + fmt2(d.dimensionTotal()) + "（" + d.avgMethod().label()
                    + "）  附加分合计 " + fmt2(d.bonusTotal()) + "  最终分 " + fmt2(d.finalScore()));
            if (!askYesNo("继续为其他候选人补录？（y=继续 / n=返回）")) {
                return;
            }
            picked = pickCandidate(svc, "分数补录——搜索下一位候选人");
            if (picked == null) {
                return;
            }
            c = svc.getCandidate(picked.studentNo());
            if (c.status() != CandidateStatus.FINISHED) {
                out.println("不能补录：该候选人当前为「" + c.status().label() + "」。返回主菜单。");
                return;
            }
        }
    }

    private void menuBonus(ScoringService svc) {
        CandidateInfo picked = pickCandidate(svc, "分数附加——选择候选人");
        if (picked == null) {
            return;
        }
        CandidateInfo c = svc.getCandidate(picked.studentNo());
        while (true) {
            BigDecimal amount = askScore("输入附加分值（0~10，最多两位小数；如才艺加分 5；q 取消）");
            if (amount == null) {
                return;
            }
            String reason = ask("加分原因（必填，如：才艺加分；q 取消）");
            if ("q".equalsIgnoreCase(reason)) {
                return;
            }
            reason = reason.trim();
            if (reason.isEmpty()) {
                out.println("原因不能为空，请重新输入（或输入 q 取消）。");
                continue;
            }
            svc.addBonus(c.studentNo(), amount, reason);
            CandidateDetail d = svc.candidateDetail(c.studentNo());
            out.println("附加成功：附加分合计 " + fmt2(d.bonusTotal()) + "。");
            if (d.finalScore() != null) {
                out.println("  最新最终分：" + fmt2(d.finalScore()));
            } else {
                out.println("  该候选人尚未结束面试，附加分将在结束面试时计入最终分。");
            }
            if (!askYesNo("继续给候选人加附加分？（y=继续 / n=返回）")) {
                return;
            }
            picked = pickCandidate(svc, "分数附加——选择下一位候选人");
            if (picked == null) {
                return;
            }
            c = svc.getCandidate(picked.studentNo());
        }
    }

    // ------------------------------------------------------------------ 8 撤销

    private void menuUndo(ScoringService svc) {
        OpLogEntry top = svc.latestOperation();
        if (top == null) {
            out.println("当前没有任何可撤销的评分操作。");
            return;
        }
        out.println("最近一条可撤销操作（共支持连续撤销，直到本批记录起点）：");
        out.println("  " + top.description());
        if (!askYesNo("确认撤销该操作？（y=撤销 / n=返回）", false)) {
            return;
        }
        UndoResult r = svc.undoLatest();
        out.println(r.description());
        out.println("  该候选人现有四维评分记录 " + r.normalCount() + " 条"
                + (r.candidateFinished() ? "，最新最终分：" + fmt2(r.finalScore())
                : "（尚未结束面试，最终分未定）"));
        if (svc.latestOperation() != null) {
            out.println("提示：可再次选择 8 继续撤销上一步。");
        }
    }

    // ------------------------------------------------------------------ 9 初始化

    private void menuReset(ScoringService svc) {
        SystemStats st = svc.stats();
        out.println("警告：初始化将执行以下操作（不可恢复）：");
        out.println("  - 清空全部四维评分记录（" + st.scoreCount() + " 条）、附加分（" + st.bonusCount() + " 条）；");
        out.println("  - 清空操作日志（此后无法再撤销）；");
        out.println("  - " + st.totalCandidates() + " 名候选人全部回到“未面试”状态；");
        out.println("  - 候选人名单本身会保留。");
        out.println("建议先通过「5 导出 CSV」备份（选择“全部”可备份未完成者的已录评分明细）。");
        String s0 = ask("是否先导出备份 CSV？y=先备份再初始化 / n=跳过备份直接初始化 / q=取消");
        char c0 = s0.isEmpty() ? 'y' : Character.toLowerCase(s0.charAt(0));
        if (c0 == 'q') {
            out.println("已取消初始化。");
            return;
        }
        if (c0 == 'y') {
            String defaultPath = "data/备份_" + LocalDateTime.now().format(FILE_TS) + ".csv";
            String input = ask("备份文件路径（回车 = 默认 " + defaultPath + "；q 取消）");
            if ("q".equalsIgnoreCase(input)) {
                out.println("已取消初始化。");
                return;
            }
            Path target = Path.of(input.isEmpty() ? defaultPath : input);
            try {
                int n = svc.exportCsv(target, true);
                out.println("备份已导出 " + n + " 行 → " + target.toAbsolutePath());
            } catch (IOException e) {
                out.println("备份导出失败，初始化已取消：" + e.getMessage());
                return;
            }
        }
        String s1 = ask("二次确认：初始化将清空全部评分与面试状态，且无法撤销。\n"
                + "如确需执行，请输入 YES（其它输入均视为取消）：");
        if (!"yes".equalsIgnoreCase(s1.trim())) {
            out.println("已取消初始化。");
            return;
        }
        svc.resetAll();
        out.println("初始化完成：全部评分与面试状态已清空，候选人名单已保留（全部为“未面试”）。");
    }

    // ------------------------------------------------------------------ 10 名单管理

    /** 名单管理子菜单：新增/改名/改学号（级联迁移）/删除（级联删除）/查看。 */
    private void menuManage(ScoringService svc) {
        while (true) {
            out.println();
            out.println("------ 名单管理（手动单条维护；导入文件 = 批量来源，二者是同一张候选人表） ------");
            out.println(" 1. 新增候选人");
            out.println(" 2. 修改姓名");
            out.println(" 3. 修改学号（自动级联迁移其评分/附加分/日志）");
            out.println(" 4. 删除候选人（自动级联删除其评分/附加分/日志）");
            out.println(" 5. 查看全部名单");
            out.println(" 0. 返回主菜单");
            int c = askInt("请选择", 0, 5);
            switch (c) {
                case 0 -> {
                    return;
                }
                case 1 -> {
                    String name = ask("输入姓名（2~64 个汉字，少数民族姓名用 · 连接；q 取消）");
                    if ("q".equalsIgnoreCase(name)) {
                        continue;
                    }
                    String no = ask("输入学号（恰好 10 位数字；q 取消）");
                    if ("q".equalsIgnoreCase(no)) {
                        continue;
                    }
                    CandidateInfo added = svc.addCandidate(name, no);
                    out.println("已新增：" + added.name() + "（" + added.studentNo() + "）。");
                }
                case 2 -> {
                    CandidateInfo picked = pickCandidate(svc, "改名——选择候选人");
                    if (picked == null) {
                        continue;
                    }
                    String name = ask("输入新姓名（q 取消）");
                    if ("q".equalsIgnoreCase(name)) {
                        continue;
                    }
                    CandidateInfo updated = svc.updateCandidateName(picked.studentNo(), name);
                    out.println("已改名：" + updated.name() + "（" + updated.studentNo() + "）。");
                }
                case 3 -> {
                    CandidateInfo picked = pickCandidate(svc, "改学号——选择候选人");
                    if (picked == null) {
                        continue;
                    }
                    String newNo = ask("输入新学号（恰好 10 位数字；q 取消）");
                    if ("q".equalsIgnoreCase(newNo)) {
                        continue;
                    }
                    CandidateInfo updated = svc.updateCandidateStudentNo(picked.studentNo(), newNo);
                    out.println("已改学号：由 " + picked.studentNo() + " 改为 " + updated.studentNo()
                            + "（评分/附加分/日志已级联迁移）。");
                }
                case 4 -> {
                    CandidateInfo picked = pickCandidate(svc, "删除候选人——选择候选人");
                    if (picked == null) {
                        continue;
                    }
                    CandidateDetail d = svc.candidateDetail(picked.studentNo());
                    out.println("将删除候选人：" + picked.name() + "（" + picked.studentNo() + "，"
                            + picked.status().label() + "），并级联删除其四维评分记录 "
                            + d.scores().size() + " 条、附加分 " + d.bonuses().size() + " 条、相关操作日志。");
                    if (!askYesNo("确认删除？（不可撤销；y=删除 / n=取消）", false)) {
                        continue;
                    }
                    svc.deleteCandidate(picked.studentNo());
                    out.println("已删除候选人及其全部评分数据。该学号今后可被重新添加。");
                }
                case 5 -> {
                    out.println();
                    out.println("学号        姓名                 状态          完成时间");
                    for (CandidateInfo x : svc.listCandidates()) {
                        out.println(pad(x.studentNo(), 12) + pad(x.name(), 20)
                                + pad(x.status().label(), 14)
                                + (x.finishedAt() == null ? "" : DISPLAY_TS.format(x.finishedAt())));
                    }
                }
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ 11 彻底重置（危险）

    /** 彻底重置：删除一切数据与数据库文件并原地重建（双重确认，第二重须输入「彻底重置」）。 */
    private void menuNuke(ScoringService svc) {
        SystemStats st = svc.stats();
        List<String> targets = svc.resetTargetPreview();
        out.println("⚠ 危险操作警告：彻底重置将删除【一切数据】（不可恢复）：");
        out.println("  - 候选人名单 " + st.totalCandidates() + " 人、四维评分记录 " + st.scoreCount()
                + " 条、附加分 " + st.bonusCount() + " 条、全部操作日志；");
        out.println("  - 数据库文件本身及伴生文件、以及数据目录下的导出/备份 CSV，将删除以下文件：");
        for (String t : targets) {
            out.println("      · " + t);
        }
        out.println("  - 与「初始化系统」不同：初始化保留候选人名单，彻底重置回到全新安装状态。");
        out.println("建议先通过「5 导出 CSV」留档。");
        String s0 = ask("是否先导出留档 CSV？y=先导出 / n=跳过 / q=取消");
        char c0 = s0.isEmpty() ? 'y' : Character.toLowerCase(s0.charAt(0));
        if (c0 == 'q') {
            out.println("已取消彻底重置。");
            return;
        }
        if (c0 == 'y') {
            String defaultPath = "data/留档_" + LocalDateTime.now().format(FILE_TS) + ".csv";
            String input = ask("留档文件路径（回车 = 默认 " + defaultPath + "；q 取消）");
            if ("q".equalsIgnoreCase(input)) {
                out.println("已取消彻底重置。");
                return;
            }
            Path target = Path.of(input.isEmpty() ? defaultPath : input);
            try {
                int n = svc.exportCsv(target, true);
                out.println("留档已导出 " + n + " 行 → " + target.toAbsolutePath());
            } catch (IOException e) {
                out.println("留档导出失败，彻底重置已取消：" + e.getMessage());
                return;
            }
        }
        if (!askYesNo("第一次确认：确认执行彻底重置？（y=继续 / n=取消）", false)) {
            out.println("已取消彻底重置。");
            return;
        }
        String s1 = ask("第二次确认：请输入确认词【彻底重置】（其它输入均视为取消）：");
        if (!"彻底重置".equals(s1.trim())) {
            out.println("确认词不符，已取消彻底重置。");
            return;
        }
        List<String> deleted = svc.resetEverything();
        out.println("彻底重置完成：系统已重置为全新状态。");
        out.println("已删除 " + deleted.size() + " 个文件（数据库已原地重建，可直接重新导入名单）。");
        for (String f : deleted) {
            out.println("    · " + f);
        }
    }

    // ------------------------------------------------------------------ 公共交互工具

    /** 搜索并让用户确认选择一位候选人；返回 null 表示取消。 */
    private CandidateInfo pickCandidate(ScoringService svc, String title) {
        out.println();
        out.println("------ " + title + " ------");
        while (true) {
            String kw = ask("输入姓名（部分即可）或学号（可输前几位）进行搜索（q 取消）");
            if ("q".equalsIgnoreCase(kw)) {
                return null;
            }
            List<CandidateInfo> found;
            try {
                found = svc.searchCandidates(kw);
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage());
                continue;
            }
            if (found.isEmpty()) {
                out.println("没有匹配的候选人，请检查姓名/学号后重试。");
                continue;
            }
            out.println("匹配结果：");
            for (int i = 0; i < found.size(); i++) {
                CandidateInfo c = found.get(i);
                out.println("  " + (i + 1) + ". " + c.studentNo() + "  " + c.name()
                        + "（" + c.status().label() + "）");
            }
            int sel = askInt("输入序号确认，或 0 重新搜索", 0, found.size());
            if (sel == 0) {
                continue;
            }
            CandidateInfo c = found.get(sel - 1);
            CandidateDetail d = svc.candidateDetail(c.studentNo());
            out.println("已确认：" + c.name() + "（" + c.studentNo() + "，"
                    + c.status().label() + (d.scores().isEmpty() ? "）" : "，已录评分 " + d.scores().size() + " 条）"));
            return c;
        }
    }

    /** 打印候选人完整明细（四维评分记录、附加分、四维平均与最终分拆解）。 */
    private void printDetail(CandidateDetail d) {
        CandidateInfo c = d.candidate();
        out.println();
        out.println("候选人：" + c.name() + "（" + c.studentNo() + "）  状态：" + c.status().label()
                + (c.finishedAt() == null ? "" : "  完成于 " + DISPLAY_TS.format(c.finishedAt())));
        if (d.scores().isEmpty()) {
            out.println("  四维评分：暂无");
        } else {
            out.println("  四维评分记录（" + d.scores().size() + " 条）：");
            for (int i = 0; i < d.scores().size(); i++) {
                ScoreItem s = d.scores().get(i);
                out.println("    " + (i + 1) + ") 责任心 " + fmt2(s.r()) + " ｜ 时间管理能力 " + fmt2(s.t())
                        + " ｜ 学生工作能力 " + fmt2(s.s()) + " ｜ 部门契合度 " + fmt2(s.f()));
            }
            out.println("  四维平均：责任心 " + fmt2(d.rAvg()) + " ｜ 时间管理能力 " + fmt2(d.tAvg())
                    + " ｜ 学生工作能力 " + fmt2(d.sAvg()) + " ｜ 部门契合度 " + fmt2(d.fAvg()));
            out.println("  四维合计：" + fmt2(d.dimensionTotal()) + "（" + d.avgMethod().label() + "）");
        }
        if (d.bonuses().isEmpty()) {
            out.println("  附加分：暂无");
        } else {
            StringBuilder sb = new StringBuilder("  附加分：");
            for (int i = 0; i < d.bonuses().size(); i++) {
                BonusItem b = d.bonuses().get(i);
                if (i > 0) {
                    sb.append("；");
                }
                sb.append("+").append(fmt2(b.amount())).append("（").append(b.reason()).append("）");
            }
            sb.append("  合计 ").append(fmt2(d.bonusTotal()));
            out.println(sb);
        }
        if (d.finalScore() != null) {
            out.println("  最终分：" + fmt2(d.finalScore()) + "（= 四维合计 " + fmt2(d.dimensionTotal())
                    + " + 附加分 " + fmt2(d.bonusTotal()) + "）");
        } else {
            out.println("  最终分：未确定（该候选人尚未结束面试）。");
        }
    }

    // ------------------------------------------------------------------ 输入工具

    private String ask(String prompt) {
        out.println(prompt);
        out.print("> ");
        out.flush();
        String line;
        try {
            line = in.readLine();
        } catch (IOException e) {
            throw new EndOfInput();
        }
        if (line == null) {
            throw new EndOfInput();
        }
        return line.trim();
    }

    private boolean askYesNo(String prompt) {
        return askYesNo(prompt, true);
    }

    /** @param defaultYes 直接回车时的默认答复；破坏性操作请传 false。 */
    private boolean askYesNo(String prompt, boolean defaultYes) {
        String s = ask(prompt);
        if (s.isEmpty()) {
            return defaultYes;
        }
        char ch = Character.toLowerCase(s.charAt(0));
        if (ch == 'y') {
            return true;
        }
        if (ch == 'n' || ch == 'q') {
            return false;
        }
        return defaultYes;
    }

    private int askInt(String prompt, int min, int max) {
        while (true) {
            String s = ask(prompt);
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
            out.println("请输入 " + min + "～" + max + " 之间的数字。");
        }
    }

    /** 读取一个数值（范围由调用方/core 校验）；返回 null 表示用户取消。 */
    private BigDecimal askScore(String prompt) {
        while (true) {
            String s = ask(prompt);
            if ("q".equalsIgnoreCase(s)) {
                return null;
            }
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                out.println("无法识别数字，请重新输入（如 22.5）。");
            }
        }
    }

    private static String fmt2(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String pad(String s, int width) {
        int d = width - displayWidth(s);
        return d <= 0 ? s : s + " ".repeat(d);
    }

    private static String padLeft(String s, int width) {
        int d = width - displayWidth(s);
        return d <= 0 ? s : " ".repeat(d) + s;
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return w;
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)
                || (cp >= 0x2E80 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE30 && cp <= 0xFE4F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x20000 && cp <= 0x3FFFD);
    }

    /** 输入流结束（管道脚本输入耗尽）时抛出，由 runLoop 捕获后干净退出。 */
    private static final class EndOfInput extends RuntimeException {
    }
}
