package scoring.gui.controller;

import java.math.BigDecimal;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import scoring.core.ScoringService;
import scoring.core.dto.BonusItem;
import scoring.core.dto.CandidateDetail;
import scoring.core.dto.CandidateStatus;
import scoring.core.dto.ScoreItem;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;
import scoring.gui.ui.Formatters;
import scoring.gui.ui.ScoreInputValidator;

/**
 * 候选人明细页（二期：四维）：
 * <ul>
 *   <li>四维评分记录表（列 = 序号/四维/录入时间）、附加分表；</li>
 *   <li>汇总卡：四个维度平均、四维合计、平均方式、附加合计、最终分（动态计算）；</li>
 *   <li>补录表单 = 四维四输入（仅 FINISHED，否则提示原因）；附加分表单（单笔 0~10、原因必填）；</li>
 *   <li>名单管理操作组（新增/改名/改学号/删除，级联提示见 NameListCard）。</li>
 * </ul>
 */
public final class DetailController implements Page {

    private final AppContext ctx;
    private final BorderPane root;

    private Label headTitle;
    private Label headBadge;
    private Label headTime;
    private TableView<ScoreItem> scoreTable;
    private TableView<BonusItem> bonusTable;
    private Label rAvgLabel;
    private Label tAvgLabel;
    private Label sAvgLabel;
    private Label fAvgLabel;
    private Label dimTotalLabel;
    private Label methodLabel;
    private Label bonusTotalLabel;
    private Label finalLabel;

    /** 补录四维输入框（顺序同 DIM_LABELS）。 */
    private TextField[] makeupFields;
    private Button makeupBtn;
    private Label makeupHint;
    private Label makeupStatus;

    private TextField bonusAmountField;
    private TextField bonusReasonField;
    private Label bonusStatus;

    private NameListCard nameCard;

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
        Label sub = new Label("四维平均与最终分由明细动态计算；写操作完成后本页自动刷新。");
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

        // 四维评分记录表
        scoreTable = new TableView<>();
        scoreTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        scoreTable.setPlaceholder(new Label("暂无四维评分记录。"));
        TableColumn<ScoreItem, String> idxCol = new TableColumn<>("序号");
        idxCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(scoreTable.getItems().indexOf(c.getValue()) + 1)));
        TableColumn<ScoreItem, String> rCol = new TableColumn<>(ScoringService.DIM_LABELS[0]);
        rCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().r())));
        TableColumn<ScoreItem, String> tCol = new TableColumn<>(ScoringService.DIM_LABELS[1]);
        tCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().t())));
        TableColumn<ScoreItem, String> sCol = new TableColumn<>(ScoringService.DIM_LABELS[2]);
        sCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().s())));
        TableColumn<ScoreItem, String> fCol = new TableColumn<>(ScoringService.DIM_LABELS[3]);
        fCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().f())));
        TableColumn<ScoreItem, String> timeCol = new TableColumn<>("录入时间");
        timeCol.setCellValueFactory(c ->
                new SimpleStringProperty(Formatters.fmtTime(c.getValue().addedAt())));
        scoreTable.getColumns().setAll(idxCol, rCol, tCol, sCol, fCol, timeCol);
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

        // 汇总卡：四维平均 + 合计 + 方式 + 附加 + 最终分
        rAvgLabel = summaryValue();
        tAvgLabel = summaryValue();
        sAvgLabel = summaryValue();
        fAvgLabel = summaryValue();
        dimTotalLabel = summaryValue();
        methodLabel = summaryValue();
        bonusTotalLabel = summaryValue();
        finalLabel = summaryValue();
        finalLabel.getStyleClass().add("final-score");
        GridPane summary = new GridPane();
        summary.setHgap(24);
        summary.setVgap(8);
        summary.add(summaryItem(ScoringService.DIM_LABELS[0] + "均", rAvgLabel), 0, 0);
        summary.add(summaryItem(ScoringService.DIM_LABELS[1] + "均", tAvgLabel), 1, 0);
        summary.add(summaryItem(ScoringService.DIM_LABELS[2] + "均", sAvgLabel), 2, 0);
        summary.add(summaryItem(ScoringService.DIM_LABELS[3] + "均", fAvgLabel), 3, 0);
        summary.add(summaryItem("四维合计", dimTotalLabel), 0, 1);
        summary.add(summaryItem("平均方式", methodLabel), 1, 1);
        summary.add(summaryItem("附加分合计", bonusTotalLabel), 2, 1);
        summary.add(summaryItem("最终分", finalLabel), 3, 1);
        VBox summaryCard = new VBox(10, summary);
        summaryCard.getStyleClass().add("card");

        nameCard = new NameListCard(ctx);

        VBox left = new VBox(16,
                headCard,
                titledCard("四维评分记录", scoreTable),
                titledCard("附加分", bonusTable),
                summaryCard);
        HBox.setHgrow(left, Priority.ALWAYS);

        VBox right = new VBox(16, buildMakeupCard(), buildBonusCard(), nameCard);
        right.setPrefWidth(400);

        HBox body = new HBox(20, left, right);
        body.setPadding(new Insets(24));
        // 明细内容较高（四维表+汇总+三个表单），包一层 ScrollPane 防小窗口裁切
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("page");
        VBox wrapper = new VBox(sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
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

    /** 补录表单：四维四输入（0~25），回车依次流转，末框回车提交。 */
    private VBox buildMakeupCard() {
        Label t = new Label("补录四维评分（仅已结束面试）");
        t.getStyleClass().add("card-title");
        makeupHint = new Label();
        makeupHint.getStyleClass().add("hint-text");
        makeupHint.setWrapText(true);
        makeupFields = new TextField[ScoringService.DIM_LABELS.length];
        HBox row = new HBox(8);
        for (int i = 0; i < makeupFields.length; i++) {
            TextField tf = new TextField();
            tf.setPromptText(ScoringService.DIM_LABELS[i]);
            final int idx = i;
            tf.setOnAction(e -> {
                if (idx < makeupFields.length - 1) {
                    makeupFields[idx + 1].requestFocus();
                } else {
                    doMakeup();
                }
            });
            HBox.setHgrow(tf, Priority.ALWAYS);
            makeupFields[i] = tf;
            row.getChildren().add(tf);
        }
        makeupBtn = new Button("补录");
        makeupBtn.getStyleClass().add("button-primary");
        makeupBtn.setOnAction(e -> doMakeup());
        makeupStatus = new Label();
        makeupStatus.getStyleClass().add("status-ok");
        makeupStatus.setWrapText(true);
        VBox card = new VBox(10, t, makeupHint, row, makeupBtn, makeupStatus);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildBonusCard() {
        Label t = new Label("附加分（如才艺加分）");
        t.getStyleClass().add("card-title");
        Label hint = new Label("任意状态均可添加；单笔 0~10（可多笔）、原因必填（≤200 字）。"
                + "附加分直接计入最终分、不参与四维去极值平均。");
        hint.getStyleClass().add("hint-text");
        hint.setWrapText(true);
        bonusAmountField = new TextField();
        bonusAmountField.setPromptText("附加分值 0~10");
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
        BigDecimal[] dims = new BigDecimal[makeupFields.length];
        for (int i = 0; i < makeupFields.length; i++) {
            String err = ScoreInputValidator.validateDim(makeupFields[i].getText(),
                    ScoringService.DIM_LABELS[i]);
            if (err != null) {
                Dialogs.error(err);
                makeupFields[i].requestFocus();
                return;
            }
            dims[i] = ScoreInputValidator.parse(makeupFields[i].getText());
        }
        try {
            ctx.svc().addMakeupScore(studentNo, dims[0], dims[1], dims[2], dims[3]);
            for (TextField tf : makeupFields) {
                tf.clear();
            }
            refresh();
            makeupStatus.setText("✓ 补录成功，最终分已自动重算。");
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doBonus() {
        String err = ScoreInputValidator.validateBonus(bonusAmountField.getText());
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

            rAvgLabel.setText(Formatters.fmt2(d.rAvg()));
            tAvgLabel.setText(Formatters.fmt2(d.tAvg()));
            sAvgLabel.setText(Formatters.fmt2(d.sAvg()));
            fAvgLabel.setText(Formatters.fmt2(d.fAvg()));
            dimTotalLabel.setText(d.dimensionTotal() == null
                    ? Formatters.PLACEHOLDER : Formatters.fmt2(d.dimensionTotal()));
            methodLabel.setText(d.scores().isEmpty() ? Formatters.PLACEHOLDER : d.avgMethod().label());
            bonusTotalLabel.setText(Formatters.fmt2(d.bonusTotal()));
            finalLabel.setText(d.finalScore() == null
                    ? "未确定" : Formatters.fmt2(d.finalScore()));

            boolean finished = d.candidate().status() == CandidateStatus.FINISHED;
            for (TextField tf : makeupFields) {
                tf.setDisable(!finished);
            }
            makeupBtn.setDisable(!finished);
            if (finished) {
                makeupHint.setText("补录一条完整四维评分（各维 0~25），提交后按最新明细整体重算最终分。");
            } else if (d.candidate().status() == CandidateStatus.PENDING) {
                makeupHint.setText("不可补录：该候选人尚未开始面试。");
            } else {
                makeupHint.setText("不可补录：该候选人正在面试中，请在「面试打分」页直接添加评分。");
            }

            // 名单管理：绑定当前候选人；面试中禁止改号/删除（按钮置灰，core 亦兜底）
            nameCard.onBind(
                    this::refresh,
                    newNo -> {
                        setStudentNo(newNo);
                        refresh();
                    },
                    () -> ctx.nav().show("candidates"));
            nameCard.bindTarget(studentNo, d.candidate().status() == CandidateStatus.INTERVIEWING);
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }
}
