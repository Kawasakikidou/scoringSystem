package scoring.gui.controller;

import java.io.File;
import java.io.IOException;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import scoring.core.dto.ImportReport;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;

/**
 * 名单导入页：文件选择（txt/csv，可多次导入，幂等）→ 展示 ImportReport。
 */
public final class ImportController implements Page {

    private final AppContext ctx;
    private final BorderPane root;
    private final VBox resultBox;

    public ImportController(AppContext ctx) {
        this.ctx = ctx;
        this.root = new BorderPane();
        root.getStyleClass().add("page");
        root.setTop(buildHeader());

        Label tip = new Label("支持 Excel（.xlsx / .xls，按文件自动识别）与 txt / csv 文本名单"
                + "（文本自动识别 UTF-8 / GB18030）。每行一人：一段 ≥2 个汉字的姓名 + 恰好 10 位数字学号。"
                + "重复导入安全：同一学号只会更新姓名，不会重复。");
        tip.getStyleClass().add("hint-text");
        tip.setWrapText(true);

        Button pick = new Button("选择名单文件并导入…");
        pick.getStyleClass().add("button-primary");
        pick.setOnAction(e -> doImport());

        VBox pickCard = new VBox(12, tip, pick);
        pickCard.getStyleClass().add("card");

        resultBox = new VBox();
        resultBox.setPadding(new Insets(0));

        VBox body = new VBox(20, pickCard, resultBox);
        body.setPadding(new Insets(24));
        root.setCenter(body);
    }

    private VBox buildHeader() {
        Label title = new Label("名单导入");
        title.getStyleClass().add("page-title");
        Label sub = new Label("导入报名名单；统计口径与《接口文档》§4.4 一致。");
        sub.getStyleClass().add("page-subtitle");
        VBox box = new VBox(4, title, sub);
        box.getStyleClass().add("page-header");
        return box;
    }

    private void doImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择名单文件");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("名单文件 (*.txt, *.csv, *.xlsx, *.xls)",
                        "*.txt", "*.csv", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("所有文件", "*.*"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            ImportReport r = ctx.svc().importRoster(file.toPath());
            showReport(file.getName(), r);
        } catch (IOException e) {
            Dialogs.error("无法读取文件「" + file.getPath() + "」：" + e.getMessage());
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    /** 在页面内渲染最近一次导入报告（保留可见），并弹出结果对话框。 */
    private void showReport(String fileName, ImportReport r) {
        VBox card = buildReportCard(fileName, r);
        resultBox.getChildren().setAll(card);
        Dialogs.custom("导入结果", buildReportCard(fileName, r));
    }

    private VBox buildReportCard(String fileName, ImportReport r) {
        Label head = new Label("导入完成：「" + fileName + "」（编码识别：" + r.encoding() + "）");
        head.getStyleClass().add("card-title");
        Label line1 = new Label("成功解析 " + r.parsedLines() + " 行（新增 " + r.added()
                + " 人、更新/去重 " + r.updated() + " 行）");
        Label line2 = new Label(r.parsedLines() == 0
                ? "跳过 " + r.skippedLines() + " 行（整个文件没有一行可解析为候选人）"
                : "跳过 " + r.skippedLines() + " 行（题头/表头与空行不计入）");
        VBox card = new VBox(8, head, line1, line2);
        card.getStyleClass().add("card");
        for (String sample : r.skippedSamples()) {
            Label s = new Label("跳过示例：" + sample);
            s.getStyleClass().add("hint-text");
            s.setWrapText(true);
            card.getChildren().add(s);
        }
        if (r.skippedLines() > r.skippedSamples().size()) {
            Label more = new Label("……（更多跳过行未显示，请检查源文件格式）");
            more.getStyleClass().add("hint-text");
            card.getChildren().add(more);
        }
        card.setPadding(new Insets(16));
        card.setMaxWidth(560);
        return card;
    }

    @Override
    public Region view() {
        return root;
    }

    @Override
    public void refresh() {
        // 页面内容以用户操作为驱动，无需每次刷新；保留最近一次导入结果展示。
    }
}
