package scoring.gui.controller;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import scoring.core.dto.SystemStats;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;

/**
 * 初始化系统（resetAll）的对话框向导，防护级别与《接口文档》§8.4 一致：
 * ① stats() 风险提示 → ②询问是否先导出备份（可选导出全部范围 CSV）→
 * ③输入框手输 YES 二次确认 → ④resetAll()。
 */
public final class ResetFlow {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ResetFlow() {
    }

    /** 从侧栏入口启动初始化向导；完成或取消后返回 true 表示已执行初始化。 */
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
                        + "・清空全部普通评分（" + st.scoreCount() + " 条）、附加分（" + st.bonusCount() + " 条）；\n"
                        + "・清空操作日志（此后无法再撤销）；\n"
                        + "・" + st.totalCandidates() + " 名候选人全部回到「未面试」状态；\n"
                        + "・候选人名单本身会保留。\n\n"
                        + "是否继续？");
        if (!go) {
            return false;
        }

        int backup = Dialogs.choose("备份建议",
                "建议先导出备份 CSV（选择「全部」范围可保留未完成者的已录评分明细）。\n\n是否先导出备份？",
                "先导出备份", "跳过备份", "取消初始化");
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
            } catch (IOException e) {
                Dialogs.error("备份导出失败，初始化已取消：" + e.getMessage());
                return false;
            } catch (RuntimeException e) {
                Dialogs.error("备份导出失败，初始化已取消：" + e.getMessage());
                return false;
            }
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
}
