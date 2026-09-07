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
                + " ｜ 普通评分 " + st.scoreCount() + " 条 ｜ 附加分 " + st.bonusCount() + " 条");
        if (st.interviewing() > 0) {
            CandidateInfo cur = svc.currentInterviewing();
            out.println("  ※ 注意：有未完成的面试："
                    + (cur == null ? "（状态异常，可执行 9 初始化）" : cur.name() + "（" + cur.studentNo() + "）"));
        }
        out.println("--------------------------------------------------");
        out.println(" 1. 导入名单文件");
        out.println(" 2. 面试打分");
        out.println(" 3. 查看候选人名单与状态");
        out.println(" 4. 查看排名与评分明细");
        out.println(" 5. 导出 CSV（Excel 可打开）");
        out.println(" 6. 分数补录（仅限已结束面试）");
        out.println(" 7. 分数附加（如才艺加分，需填原因）");
        out.println(" 8. 撤销最近一次评分操作");
        out.println(" 9. 初始化系统（清空全部评分，保留名单）");
        out.println(" 0. 退出");
        return askInt("请输入功能编号", 0, 9);
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
                    + "）  已录普通评分 " + d.scores().size() + " 条 ======");
            out.println(" 1. 添加评分（输入 0~100 的分数）");
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
                    BigDecimal v = askScore("请输入评分（0~100，最多两位小数；q 取消）");
                    if (v == null) {
                        continue;
                    }
                    svc.addInterviewScore(cand.studentNo(), v);
                    CandidateDetail d2 = svc.candidateDetail(cand.studentNo());
                    out.println("已保存第 " + d2.scores().size() + " 条评分：" + fmt2(v)
                            + "。可继续添加，或选择 3 结束评分。");
                }
                case 2 -> {
                    if (askYesNo("确认删除该候选人最近一条评分？（可随后用主菜单 8 撤销删除）", false)) {
                        ScoreItem removed = svc.deleteLastInterviewScore(cand.studentNo());
                        out.println("已删除评分：" + fmt2(removed.value()) + "。");
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
        out.println("  平均分：" + fmt2(r.average()) + "（" + r.avgMethod().label()
                + (r.belowThree() ? "，评分不足 3 条经确认" : "") + "）");
        out.println("  附加分合计：" + fmt2(r.bonusTotal()));
        out.println("  最终分：" + fmt2(r.finalScore()));
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
        } else {
            out.println("共显示 " + shown + " 人（总数 " + all.size() + " 人）。");
        }
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
            out.println("排名  学号        姓名               最终分  平均分  平均方式        附加分合计  评分条数  完成时间");
            for (RankRow r : rows) {
                out.println(padLeft(String.valueOf(r.rank()), 5) + pad(r.candidate().studentNo(), 12)
                        + pad(r.candidate().name(), 18) + padLeft(fmt2(r.finalScore()), 8)
                        + padLeft(fmt2(r.average()), 8) + pad(r.avgMethod().label(), 16)
                        + padLeft(fmt2(r.bonusTotal()), 12) + padLeft(String.valueOf(r.normalScoreCount()), 10)
                        + (r.finishedAt() == null ? "" : DISPLAY_TS.format(r.finishedAt())));
            }
            out.println("排序规则：最终分降序；同分按学号升序。");
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
            BigDecimal v = askScore("输入补录评分（0~100；q 取消）");
            if (v == null) {
                return;
            }
            svc.addMakeupScore(c.studentNo(), v);
            CandidateDetail d = svc.candidateDetail(c.studentNo());
            out.println("补录成功：现共 " + d.scores().size() + " 条普通评分。");
            out.println("  平均分：" + fmt2(d.average()) + "（" + d.avgMethod().label() + "）"
                    + "  附加分合计：" + fmt2(d.bonusTotal())
                    + "  最终分：" + fmt2(d.finalScore()));
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
            BigDecimal amount = askScore("输入附加分值（0~100；如才艺加分 5；q 取消）");
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
        out.println("  该候选人现有普通评分 " + r.normalCount() + " 条"
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
        out.println("  - 清空全部普通评分（" + st.scoreCount() + " 条）、附加分（" + st.bonusCount() + " 条）；");
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

    /** 打印候选人完整明细（含普通评分、附加分与最终分拆解）。 */
    private void printDetail(CandidateDetail d) {
        CandidateInfo c = d.candidate();
        out.println();
        out.println("候选人：" + c.name() + "（" + c.studentNo() + "）  状态：" + c.status().label()
                + (c.finishedAt() == null ? "" : "  完成于 " + DISPLAY_TS.format(c.finishedAt())));
        if (d.scores().isEmpty()) {
            out.println("  普通评分：暂无");
        } else {
            StringBuilder sb = new StringBuilder("  普通评分（" + d.scores().size() + " 条）：");
            for (int i = 0; i < d.scores().size(); i++) {
                if (i > 0) {
                    sb.append("；");
                }
                sb.append((i + 1)).append(") ").append(fmt2(d.scores().get(i).value()));
            }
            out.println(sb);
            out.println("  平均分：" + fmt2(d.average()) + "（" + d.avgMethod().label() + "）");
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
            out.println("  最终分：" + fmt2(d.finalScore())
                    + "（= 平均分 " + fmt2(d.average()) + " + 附加分 " + fmt2(d.bonusTotal()) + "）");
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

    /** 读取一个分数（0~100 由 core 校验）；返回 null 表示用户取消。 */
    private BigDecimal askScore(String prompt) {
        while (true) {
            String s = ask(prompt);
            if ("q".equalsIgnoreCase(s)) {
                return null;
            }
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                out.println("无法识别数字，请重新输入（如 85 或 85.5）。");
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
