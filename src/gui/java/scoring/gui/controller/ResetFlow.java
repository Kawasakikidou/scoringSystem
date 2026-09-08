package scoring.gui.controller;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import scoring.core.dto.SystemStats;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;

/**
 * 两个高危操作的对话框向导（防护级别与《接口文档》§8.4/§8.6 一致）：
 * <ul>
 *   <li>{@link #run}：初始化系统（resetAll）——清空评分/附加/日志，候选人回未面试（名单保留）；
 *       风险提示 → 可选备份 → 手输 YES；</li>
 *   <li>{@link #runEverything}：彻底重置（resetEverything）——删除一切数据（连名单与库文件、
 *       数据目录 CSV 一并删除，原地重建空库）；文件清单预览 → 可选留档 → 普通确认 →
 *       手输确认词「彻底重置」→ 展示已删除文件列表。</li>
 * </ul>
 */
public final class ResetFlow {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ResetFlow() {
    }

    /** 从侧栏入口启动初始化向导；返回 true 表示已执行初始化。 */
    public static boolean run(AppContext ctx, Window owner) {
        SystemStats st;
        try {
            st = ctx.svc().stats();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
            return false;
        }

        boolean go = Dialogs.confirm("初始化系统 — 风险提示",
                "初始化将执行以下操作（不可恢复）：\n\n"
                        + "・清空全部四维评分记录（" + st.scoreCount() + " 条）、附加分（" + st.bonusCount() + " 条）；\n"
                        + "・清空操作日志（此后无法再撤销）；\n"
                        + "・" + st.totalCandidates() + " 名候选人全部回到「未面试」状态；\n"
                        + "・候选人名单本身会保留。\n\n"
                        + "是否继续？");
        if (!go) {
            return false;
        }

        if (!askBackupAndExport(ctx, owner, "取消初始化")) {
            return false;
        }

        boolean typed = Dialogs.confirmTypedYes("二次确认",
                "初始化将清空全部评分与面试状态，且无法撤销、无法恢复。");
        if (!typed) {
            return false;
        }

        try {
            ctx.svc().resetAll();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
            return false;
        }
        Dialogs.info("初始化完成", "全部评分与面试状态已清空，候选人名单已保留（全部为「未面试」）。");
        return true;
    }

    /**
     * 彻底重置（resetEverything）向导，流程与《接口文档》§8.6 一致：
     * ① resetTargetPreview() 列出将删除的文件与统计风险 → ②可选「先导出留档」（范围=全部）→
     * ③第一次普通确认 → ④手输确认词「彻底重置」→ ⑤resetEverything() 并展示已删除文件列表。
     *
     * @return true 表示已执行彻底重置（调用方应刷新全部视图）
     */
    public static boolean runEverything(AppContext ctx, Window owner) {
        List<String> targets;
        SystemStats st;
        try {
            targets = ctx.svc().resetTargetPreview();
            st = ctx.svc().stats();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
            return false;
        }

        StringBuilder files = new StringBuilder();
        for (String t : targets) {
            files.append("・").append(t).append("\n");
        }
        if (files.isEmpty()) {
            files.append("（当前没有可删除的库文件或数据目录 CSV）\n");
        }
        Label fileList = new Label(files.toString());
        fileList.setWrapText(true);
        Label risk = new Label("统计风险：名单 " + st.totalCandidates() + " 人、四维评分记录 "
                + st.scoreCount() + " 条、附加分 " + st.bonusCount() + " 条——将随库文件全部删除。");
        risk.setWrapText(true);
        VBox content = new VBox(8,
                new Label("彻底重置将删除以下文件（数据库/伴生文件/数据目录 CSV），并原地重建空库："),
                fileList, risk);
        content.setPadding(new Insets(16));
        Dialogs.custom("彻底重置 — 将删除的文件与风险", content);

        if (!askBackupAndExport(ctx, owner, "取消彻底重置")) {
            return false;
        }

        boolean go = Dialogs.confirm("彻底重置 — 第一次确认",
                "彻底重置与「初始化系统」不同：候选人名单也会被删除，数据库文件与数据目录下的"
                        + "导出/备份 CSV 一并删除，且不可恢复。\n\n是否继续？");
        if (!go) {
            return false;
        }

        boolean typed = Dialogs.confirmTyped("彻底重置 — 最终确认",
                "即将删除一切数据（含名单与数据库文件）并原地重建空库。",
                "彻底重置", "确认彻底重置");
        if (!typed) {
            return false;
        }

        List<String> deleted;
        try {
            deleted = ctx.svc().resetEverything();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
            return false;
        }

        StringBuilder done = new StringBuilder("已删除文件：\n");
        for (String f : deleted) {
            done.append("・").append(f).append("\n");
        }
        done.append("\n系统已回到全新空状态：可重新导入名单或手动新增候选人。");
        Dialogs.info("彻底重置完成", done.toString());
        return true;
    }

    /**
     * 备份询问（两个向导共用）：三选「先导出备份 / 跳过 / <cancelText>」。
     *
     * @return true = 继续（导出成功或用户选择跳过）；false = 取消或导出失败
     */
    private static boolean askBackupAndExport(AppContext ctx, Window owner, String cancelText) {
        int backup = Dialogs.choose("备份建议",
                "建议先导出备份 CSV（选择「全部」范围可保留未完成者的已录评分明细）。\n\n是否先导出备份？",
                "先导出备份", "跳过备份", cancelText);
        if (backup < 0 || backup == 2) {
            return false;
        }
        if (backup == 0) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("保存备份 CSV");
            chooser.setInitialFileName("备份_" + LocalDateTime.now().format(FILE_TS) + ".csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV 文件", "*.csv"));
            File dataDir = new File("data");
            if (dataDir.isDirectory()) {
                chooser.setInitialDirectory(dataDir.getAbsoluteFile());
            }
            File file = chooser.showSaveDialog(owner);
            if (file == null) {
                return false;
            }
            try {
                int n = ctx.svc().exportCsv(file.toPath(), true);
                Dialogs.info("备份完成", "备份已导出 " + n + " 行 → " + file.getAbsolutePath());
            } catch (IOException | RuntimeException e) {
                Dialogs.error("备份导出失败，操作已取消：" + e.getMessage());
                return false;
            }
        }
        return true;
    }
}
