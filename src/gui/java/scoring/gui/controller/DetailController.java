package scoring.gui.controller;

import java.math.BigDecimal;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import scoring.core.dto.BonusItem;
import scoring.core.dto.CandidateDetail;
import scoring.core.dto.CandidateStatus;
import scoring.core.dto.ScoreItem;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;
import scoring.gui.ui.Formatters;
import scoring.gui.ui.ScoreInputValidator;

/**
 * 候选人明细页：普通评分表 / 附加分表 / 平均方式 / 最终分（candidateDetail 唯一数据源），
 * 以及「补录」（仅 FINISHED）与「附加分」（原因必填）两个表单。
 */
public final class DetailController implements Page {

    private final AppContext ctx;
    private final BorderPane root;

    private Label headTitle;
    private Label headBadge;
    private Label headTime;
    private TableView<ScoreItem> scoreTable;
    private TableView<BonusItem> bonusTable;
    private Label avgLabel;
    private Label methodLabel;
    private Label bonusTotalLabel;
    private Label finalLabel;

    private TextField makeupField;
    private Button makeupBtn;
    private Label makeupHint;
    private Label makeupStatus;

    private TextField bonusAmountField;
    private TextField bonusReasonField;
    private Label bonusStatus;

    private String studentNo;

    public DetailController(AppContext ctx) {
        this.ctx = ctx;
        this.root = new BorderPane();
        root.getStyleClass().add("page");
        root.setTop(buildHeader());
        root.setCenter(buildBody());
    }

    private VBox buildHeader() {
        Label title = new Label("候选人明细");
        title.getStyleClass().add("page-title");
        Label sub = new Label("平均分与最终分由明细动态计算；写操作完成后本页自动刷新。");
        sub.getStyleClass().add("page-subtitle");
        VBox box = new VBox(4, title, sub);
        box.getStyleClass().add("page-header");
        return box;
    }

    @SuppressWarnings("unchecked")
    private VBox buildBody() {
        // 候选人信息卡
        headTitle = new Label("—");
        headTitle.getStyleClass().add("card-title");
        headBadge = new Label();
        headBadge.getStyleClass().add("badge");
        headTime = new Label();
        headTime.getStyleClass().add("hint-text");
        HBox headLine = new HBox(12, headTitle, headBadge);
        headLine.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox headCard = new VBox(8, headLine, headTime);
        headCard.getStyleClass().add("card");

        // 普通评分表
        scoreTable = new TableView<>();
        scoreTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        scoreTable.setPlaceholder(new Label("暂无普通评分。"));
        TableColumn<ScoreItem, String> idxCol = new TableColumn<>("序号");
        idxCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(scoreTable.getItems().indexOf(c.getValue()) + 1)));
        TableColumn<ScoreItem, String> valueCol = new TableColumn<>("分值");
        valueCol.setCellValueFactory(c ->
                new SimpleStringProperty(Formatters.fmt2(c.getValue().value())));
        TableColumn<ScoreItem, String> timeCol = new TableColumn<>("录入时间");
        timeCol.setCellValueFactory(c ->
                new SimpleStringProperty(Formatters.fmtTime(c.getValue().addedAt())));
        scoreTable.getColumns().setAll(idxCol, valueCol, timeCol);
        scoreTable.setPrefHeight(190);

        // 附加分表
        bonusTable = new TableView<>();
        bonusTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        bonusTable.setPlaceholder(new Label("暂无附加分。"));
        TableColumn<BonusItem, String> bValueCol = new TableColumn<>("附加分值");
        bValueCol.setCellValueFactory(c ->
                new SimpleStringProperty("+" + Formatters.fmt2(c.getValue().amount())));
        TableColumn<BonusItem, String> bReasonCol = new TableColumn<>("原因");
        bReasonCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().reason()));
        TableColumn<BonusItem, String> bTimeCol = new TableColumn<>("记录时间");
        bTimeCol.setCellValueFactory(c ->
                new SimpleStringProperty(Formatters.fmtTime(c.getValue().addedAt())));
        bonusTable.getColumns().setAll(bValueCol, bReasonCol, bTimeCol);
        bonusTable.setPrefHeight(160);

        // 汇总卡
        avgLabel = summaryValue();
        methodLabel = summaryValue();
        bonusTotalLabel = summaryValue();
        finalLabel = summaryValue();
        finalLabel.getStyleClass().add("final-score");
        GridPane summary = new GridPane();
        summary.setHgap(28);
        summary.setVgap(8);
        summary.add(summaryItem("平均分", avgLabel), 0, 0);
        summary.add(summaryItem("平均方式", methodLabel), 1, 0);
        summary.add(summaryItem("附加分合计", bonusTotalLabel), 2, 0);
        summary.add(summaryItem("最终分", finalLabel), 3, 0);
        VBox summaryCard = new VBox(10, summary);
        summaryCard.getStyleClass().add("card");

        VBox left = new VBox(16,
                headCard,
                titledCard("普通评分", scoreTable),
                titledCard("附加分", bonusTable),
                summaryCard);
        HBox.setHgrow(left, Priority.ALWAYS);

        VBox right = new VBox(16, buildMakeupCard(), buildBonusCard());
        right.setPrefWidth(360);

        HBox body = new HBox(20, left, right);
        body.setPadding(new Insets(24));
        VBox wrapper = new VBox(body);
        return wrapper;
    }

    private Label summaryValue() {
        Label l = new Label(Formatters.PLACEHOLDER);
        l.getStyleClass().add("stat-value");
        return l;
    }

    private VBox summaryItem(String name, Label value) {
        Label t = new Label(name);
        t.getStyleClass().add("stat-title");
        return new VBox(2, t, value);
    }

    private VBox titledCard(String title, Region content) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        VBox card = new VBox(8, t, content);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildMakeupCard() {
        Label t = new Label("补录评分（仅已结束面试）");
        t.getStyleClass().add("card-title");
        makeupHint = new Label();
        makeupHint.getStyleClass().add("hint-text");
        makeupHint.setWrapText(true);
        makeupField = new TextField();
        makeupField.setPromptText("补录分值 0~100");
        makeupField.setOnAction(e -> doMakeup());
        makeupBtn = new Button("补录");
        makeupBtn.getStyleClass().add("button-primary");
        makeupBtn.setOnAction(e -> doMakeup());
        makeupStatus = new Label();
        makeupStatus.getStyleClass().add("status-ok");
        makeupStatus.setWrapText(true);
        VBox card = new VBox(10, t, makeupHint, makeupField, makeupBtn, makeupStatus);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildBonusCard() {
        Label t = new Label("附加分（如才艺加分）");
        t.getStyleClass().add("card-title");
        Label hint = new Label("任意状态均可添加；原因必填（≤200 字）。附加分直接计入最终分、不参与去极值平均。");
        hint.getStyleClass().add("hint-text");
        hint.setWrapText(true);
        bonusAmountField = new TextField();
        bonusAmountField.setPromptText("附加分值 0~100");
        bonusReasonField = new TextField();
        bonusReasonField.setPromptText("加分原因（必填，如：才艺加分）");
        Button btn = new Button("添加附加分");
        btn.getStyleClass().add("button-primary");
        btn.setOnAction(e -> doBonus());
        bonusStatus = new Label();
        bonusStatus.getStyleClass().add("status-ok");
        bonusStatus.setWrapText(true);
        VBox card = new VBox(10, t, hint, bonusAmountField, bonusReasonField, btn, bonusStatus);
        card.getStyleClass().add("card");
        return card;
    }

    /** 由导航器在切入前设置目标学号。 */
    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    private void doMakeup() {
        String err = ScoreInputValidator.validate(makeupField.getText(), "分值");
        if (err != null) {
            Dialogs.error(err);
            return;
        }
        BigDecimal v = ScoreInputValidator.parse(makeupField.getText());
        try {
            ctx.svc().addMakeupScore(studentNo, v);
            makeupField.clear();
            refresh();
            makeupStatus.setText("✓ 补录成功，最终分已自动重算。");
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doBonus() {
        String err = ScoreInputValidator.validate(bonusAmountField.getText(), "附加分值");
        if (err != null) {
            Dialogs.error(err);
            return;
        }
        String reason = bonusReasonField.getText() == null ? "" : bonusReasonField.getText().trim();
        if (reason.isEmpty()) {
            Dialogs.error("请填写附加分原因（如：才艺加分）。");
            return;
        }
        BigDecimal amount = ScoreInputValidator.parse(bonusAmountField.getText());
        try {
            ctx.svc().addBonus(studentNo, amount, reason);
            bonusAmountField.clear();
            bonusReasonField.clear();
            refresh();
            bonusStatus.setText("✓ 附加成功。附加分合计已更新到上方汇总。");
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    @Override
    public Region view() {
        return root;
    }

    @Override
    public void refresh() {
        if (studentNo == null) {
            return;
        }
        try {
            CandidateDetail d = ctx.svc().candidateDetail(studentNo);
            headTitle.setText(d.candidate().name() + "（" + d.candidate().studentNo() + "）");
            headBadge.setText(d.candidate().status().label());
            headBadge.getStyleClass().removeAll("badge-pending", "badge-interviewing", "badge-finished");
            switch (d.candidate().status()) {
                case PENDING -> headBadge.getStyleClass().add("badge-pending");
                case INTERVIEWING -> headBadge.getStyleClass().add("badge-interviewing");
                case FINISHED -> headBadge.getStyleClass().add("badge-finished");
            }
            headTime.setText(d.candidate().finishedAt() == null
                    ? "尚未结束面试" : "完成于 " + Formatters.fmtTime(d.candidate().finishedAt()));

            scoreTable.getItems().setAll(d.scores());
            scoreTable.refresh();
            bonusTable.getItems().setAll(d.bonuses());
            bonusTable.refresh();

            avgLabel.setText(d.average() == null ? Formatters.PLACEHOLDER : Formatters.fmt2(d.average()));
            methodLabel.setText(d.scores().isEmpty() ? Formatters.PLACEHOLDER : d.avgMethod().label());
            bonusTotalLabel.setText(Formatters.fmt2(d.bonusTotal()));
            finalLabel.setText(d.finalScore() == null
                    ? "未确定" : Formatters.fmt2(d.finalScore()));

            boolean finished = d.candidate().status() == CandidateStatus.FINISHED;
            makeupField.setDisable(!finished);
            makeupBtn.setDisable(!finished);
            if (finished) {
                makeupHint.setText("补录一条普通评分，提交后按最新明细整体重算最终分。");
            } else if (d.candidate().status() == CandidateStatus.PENDING) {
                makeupHint.setText("不可补录：该候选人尚未开始面试。");
            } else {
                makeupHint.setText("不可补录：该候选人正在面试中，请在「面试打分」页直接添加评分。");
            }
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }
}
