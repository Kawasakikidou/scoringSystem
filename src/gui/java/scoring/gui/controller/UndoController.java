package scoring.gui.controller;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import scoring.core.dto.OpLogEntry;
import scoring.core.dto.UndoResult;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;
import scoring.gui.ui.Formatters;

/**
 * 撤销页：先展示 latestOperation() 预览（谁/什么操作/分值），确认后 undoLatest()，
 * 并用 UndoResult 展示结果（含最新最终分）。无日志时按钮禁用并提示。
 */
public final class UndoController implements Page {

    private final AppContext ctx;
    private final BorderPane root;

    private Label previewText;
    private Label previewTime;
    private Button undoBtn;
    private Label emptyHint;
    private final VBox resultBox;

    public UndoController(AppContext ctx) {
        this.ctx = ctx;
        this.root = new BorderPane();
        root.getStyleClass().add("page");
        root.setTop(buildHeader());

        Label cardTitle = new Label("最近一次可撤销操作");
        cardTitle.getStyleClass().add("card-title");
        previewText = new Label();
        previewText.setWrapText(true);
        previewTime = new Label();
        previewTime.getStyleClass().add("hint-text");
        undoBtn = new Button("撤销该操作");
        undoBtn.getStyleClass().add("button-danger");
        undoBtn.setOnAction(e -> doUndo());
        VBox previewCard = new VBox(10, cardTitle, previewText, previewTime, undoBtn);
        previewCard.getStyleClass().add("card");
        previewCard.setMaxWidth(560);

        emptyHint = new Label();
        emptyHint.getStyleClass().add("hint-text");
        emptyHint.setWrapText(true);

        resultBox = new VBox();

        VBox body = new VBox(16, previewCard, emptyHint, resultBox);
        body.setPadding(new Insets(24));
        root.setCenter(body);
    }

    private VBox buildHeader() {
        Label title = new Label("撤销操作");
        title.getStyleClass().add("page-title");
        Label sub = new Label("只能撤销「评分类」操作（打分/补录/删除评分/附加分），按倒序一次一步；"
                + "不能跨过初始化。撤销后自动重算该候选人最终分。");
        sub.getStyleClass().add("page-subtitle");
        sub.setWrapText(true);
        VBox box = new VBox(4, title, sub);
        box.getStyleClass().add("page-header");
        return box;
    }

    private void doUndo() {
        OpLogEntry top;
        try {
            top = ctx.svc().latestOperation();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
            return;
        }
        if (top == null) {
            refresh();
            return;
        }
        if (!Dialogs.confirm("确认撤销", "将撤销以下操作：\n\n" + top.description() + "\n\n是否确认？")) {
            return;
        }
        try {
            UndoResult r = ctx.svc().undoLatest();
            showUndoResult(r);
            refresh();
        } catch (RuntimeException e) {
            // 如「不能撤销到已结束面试只剩 0 条评分」等边界提示
            Dialogs.error(e.getMessage());
        }
    }

    private void showUndoResult(UndoResult r) {
        Label head = new Label("✓ 撤销成功");
        head.getStyleClass().add("card-title");
        VBox card = new VBox(8, head,
                new Label(r.description()),
                new Label("候选人 " + r.studentName() + "（" + r.studentNo() + "）现有普通评分 "
                        + r.normalCount() + " 条"
                        + (r.candidateFinished()
                        ? "，最新最终分：" + Formatters.fmt2(r.finalScore())
                        : "（尚未结束面试，最终分未定）")));
        card.getStyleClass().addAll("card", "result-card");
        card.setMaxWidth(560);
        resultBox.getChildren().setAll(card);
    }

    @Override
    public Region view() {
        return root;
    }

    @Override
    public void refresh() {
        OpLogEntry top;
        try {
            top = ctx.svc().latestOperation();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
            return;
        }
        if (top == null) {
            previewText.setText("（无可撤销操作）");
            previewTime.setText("");
            undoBtn.setDisable(true);
            emptyHint.setText("当前没有任何可撤销的评分操作。录入评分、补录、删除评分或添加附加分后，这里会出现可撤销的记录。");
        } else {
            previewText.setText(top.description());
            previewTime.setText("操作时间：" + Formatters.fmtTime(top.time())
                    + "　支持连续撤销，直到本批记录起点。");
            undoBtn.setDisable(false);
            emptyHint.setText("");
        }
    }
}
