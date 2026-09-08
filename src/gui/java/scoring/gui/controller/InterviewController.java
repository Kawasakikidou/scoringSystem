package scoring.gui.controller;

import java.math.BigDecimal;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import scoring.core.dto.CandidateDetail;
import scoring.core.dto.CandidateInfo;
import scoring.core.dto.FinishResult;
import scoring.core.dto.ScoreItem;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;
import scoring.gui.ui.Formatters;
import scoring.gui.ui.ScoreInputValidator;

/**
 * 面试打分页：两种视图——
 * <ul>
 *   <li>选人视图：搜索候选人并「开始面试」（已有他人面试中时不出现此视图，
 *       直接被会话视图顶替；beginInterview 的状态冲突错误也会弹出提示）；</li>
 *   <li>会话视图：逐条保存评分 / 删除上一条 / 结束评分（不足 3 条时按 core 返回的
 *       FinishResult 预览弹二次确认）。</li>
 * </ul>
 */
public final class InterviewController implements Page {

    private final AppContext ctx;
    private final BorderPane root;

    // 选人视图
    private TextField searchField;
    private TableView<CandidateInfo> pickTable;
    private Button beginBtn;
    private Label pickHint;

    // 会话视图
    private Label sessionTitle;
    private Label sessionInfo;
    private TableView<ScoreItem> scoreTable;
    private Label scoreCountLabel;
    private TextField scoreField;
    private Label statusLabel;
    private Button finishBtn;

    /** 当前会话中的候选人（null 表示选人视图）。 */
    private CandidateInfo current;
    /** 由明细页/仪表盘跳转过来时预选/恢复的学号。 */
    private String pendingNo;

    public InterviewController(AppContext ctx) {
        this.ctx = ctx;
        this.root = new BorderPane();
        root.getStyleClass().add("page");
        root.setTop(buildHeader());
    }

    private VBox buildHeader() {
        Label title = new Label("面试打分");
        title.getStyleClass().add("page-title");
        Label sub = new Label("同一时刻只能面试一位候选人；中途离开会保持「面试中」，可随时回来继续。");
        sub.getStyleClass().add("page-subtitle");
        VBox box = new VBox(4, title, sub);
        box.getStyleClass().add("page-header");
        return box;
    }

    /** 供导航器调用：预选/恢复某位候选人的面试。 */
    public void requestCandidate(String studentNo) {
        this.pendingNo = studentNo;
    }

    // ------------------------------------------------------------------ 选人视图

    @SuppressWarnings("unchecked")
    private VBox buildPickView() {
        searchField = new TextField();
        searchField.setPromptText("输入姓名（部分即可）或学号（可输前几位）搜索候选人");
        searchField.setOnAction(e -> doPickSearch());
        HBox.setHgrow(searchField, Priority.ALWAYS);
        Button searchBtn = new Button("搜索");
        searchBtn.getStyleClass().add("button-primary");
        searchBtn.setOnAction(e -> doPickSearch());
        Button allBtn = new Button("显示全部");
        allBtn.getStyleClass().add("button-secondary");
        allBtn.setOnAction(e -> {
            searchField.clear();
            doPickSearch();
        });
        HBox bar = new HBox(10, searchField, searchBtn, allBtn);

        pickTable = new TableView<>();
        pickTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        pickTable.setPlaceholder(new Label("输入关键字搜索，或点击「显示全部」。"));
        TableColumn<CandidateInfo, String> noCol = new TableColumn<>("学号");
        noCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().studentNo()));
        TableColumn<CandidateInfo, String> nameCol = new TableColumn<>("姓名");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        TableColumn<CandidateInfo, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().label()));
        pickTable.getColumns().setAll(noCol, nameCol, statusCol);
        VBox.setVgrow(pickTable, Priority.ALWAYS);
        pickTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> beginBtn.setDisable(n == null));

        beginBtn = new Button("开始面试");
        beginBtn.getStyleClass().add("button-primary");
        beginBtn.setDisable(true);
        beginBtn.setOnAction(e -> {
            CandidateInfo picked = pickTable.getSelectionModel().getSelectedItem();
            if (picked != null) {
                beginFor(picked.studentNo());
            }
        });

        pickHint = new Label("选择一位「未面试」候选人后开始；「已结束面试」不可重开（可用明细页补录/附加）。");
        pickHint.getStyleClass().add("hint-text");
        pickHint.setWrapText(true);

        VBox box = new VBox(14, bar, pickTable, pickHint, beginBtn);
        box.setPadding(new Insets(24));
        return box;
    }

    private void doPickSearch() {
        String kw = searchField.getText() == null ? "" : searchField.getText().trim();
        try {
            List<CandidateInfo> list = kw.isEmpty()
                    ? ctx.svc().listCandidates()
                    : ctx.svc().searchCandidates(kw);
            pickTable.setItems(FXCollections.observableArrayList(list));
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 会话视图

    @SuppressWarnings("unchecked")
    private VBox buildSessionView() {
        sessionTitle = new Label();
        sessionTitle.getStyleClass().add("card-title");
        sessionInfo = new Label();
        sessionInfo.getStyleClass().add("hint-text");
        VBox infoCard = new VBox(6, sessionTitle, sessionInfo);
        infoCard.getStyleClass().add("card");

        scoreTable = new TableView<>();
        scoreTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        scoreTable.setPlaceholder(new Label("还没有评分，请在下方输入第一条分值。"));
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
        VBox.setVgrow(scoreTable, Priority.ALWAYS);

        scoreCountLabel = new Label();
        scoreCountLabel.getStyleClass().add("hint-text");

        scoreField = new TextField();
        scoreField.setPromptText("输入 0~100 的分值（最多两位小数，如 85 或 85.5）");
        scoreField.setOnAction(e -> doAddScore());
        HBox.setHgrow(scoreField, Priority.ALWAYS);
        Button saveBtn = new Button("保存评分");
        saveBtn.getStyleClass().add("button-primary");
        saveBtn.setOnAction(e -> doAddScore());
        HBox inputRow = new HBox(10, scoreField, saveBtn);

        Button delBtn = new Button("删除上一条（误输入）");
        delBtn.getStyleClass().add("button-secondary");
        delBtn.setOnAction(e -> doDeleteLast());

        finishBtn = new Button("结束评分并计算最终分");
        finishBtn.getStyleClass().add("button-success");
        finishBtn.setOnAction(e -> doFinish());

        Button backBtn = new Button("暂存返回（保持面试中）");
        backBtn.getStyleClass().add("button-link");
        backBtn.setOnAction(e -> ctx.nav().show("dashboard"));

        HBox actions = new HBox(12, delBtn, finishBtn, backBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-ok");

        VBox formCard = new VBox(12, scoreCountLabel, scoreTable, inputRow, actions, statusLabel);
        formCard.getStyleClass().add("card");

        VBox box = new VBox(20, infoCard, formCard);
        box.setPadding(new Insets(24));
        return box;
    }

    // ------------------------------------------------------------------ 业务动作（全部经 core 服务）

    private void beginFor(String studentNo) {
        try {
            CandidateInfo c = ctx.svc().beginInterview(studentNo);
            enterSession(c);
        } catch (RuntimeException e) {
            // 如「同一时刻只能面试一位候选人：当前 X 正在面试中」
            Dialogs.error(e.getMessage());
        }
    }

    private void enterSession(CandidateInfo c) {
        current = c;
        pendingNo = null;
        root.setCenter(buildSessionView());
        reloadScores();
    }

    private void reloadScores() {
        if (current == null) {
            return;
        }
        try {
            CandidateDetail d = ctx.svc().candidateDetail(current.studentNo());
            sessionTitle.setText("正在面试：" + d.candidate().name()
                    + "（" + d.candidate().studentNo() + "）");
            sessionInfo.setText("状态：" + d.candidate().status().label()
                    + "　已录普通评分 " + d.scores().size() + " 条"
                    + (d.scores().size() >= 3
                    ? "（结束评分时将自动去掉 1 个最高分、1 个最低分取平均）"
                    : "（满 3 条后结束评分将自动去极值；不足 3 条结束需二次确认，按普通平均）"));
            scoreTable.getItems().setAll(d.scores());
            scoreTable.refresh();
            scoreCountLabel.setText("普通评分（" + d.scores().size() + " 条）：");
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doAddScore() {
        if (current == null) {
            return;
        }
        String text = scoreField.getText();
        String err = ScoreInputValidator.validate(text, "分值");
        if (err != null) {
            Dialogs.error(err);
            return;
        }
        BigDecimal v = ScoreInputValidator.parse(text);
        try {
            ctx.svc().addInterviewScore(current.studentNo(), v);
            scoreField.clear();
            scoreField.requestFocus();
            reloadScores();
            statusLabel.setText("✓ 已保存评分 " + Formatters.fmt2(v)
                    + "（第 " + scoreTable.getItems().size() + " 条），可继续添加。");
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doDeleteLast() {
        if (current == null) {
            return;
        }
        if (!Dialogs.confirm("删除评分", "确认删除该候选人最近一条评分？（之后可用「撤销」恢复）")) {
            return;
        }
        try {
            ScoreItem removed = ctx.svc().deleteLastInterviewScore(current.studentNo());
            reloadScores();
            statusLabel.setText("✓ 已删除评分 " + Formatters.fmt2(removed.value()) + "。");
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doFinish() {
        if (current == null) {
            return;
        }
        try {
            FinishResult first = ctx.svc().finishInterview(current.studentNo(), false);
            if (first.completed()) {
                showFinishResult(first);
                exitSession();
                return;
            }
            // 1~2 条：core 返回预览，弹二次确认（不足 3 条将按普通平均）
            boolean ok = Dialogs.confirm("评分不足 3 条 — 二次确认", first.message());
            if (!ok) {
                statusLabel.setText("未结束面试，可继续添加评分。");
                return;
            }
            FinishResult done = ctx.svc().finishInterview(current.studentNo(), true);
            showFinishResult(done);
            exitSession();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void showFinishResult(FinishResult r) {
        Label title = new Label("面试已完成");
        title.getStyleClass().add("card-title");
        VBox box = new VBox(8, title,
                new Label("平均分：" + Formatters.fmt2(r.average()) + "（" + r.avgMethod().label()
                        + (r.belowThree() ? "，评分不足 3 条经确认" : "") + "）"),
                new Label("附加分合计：" + Formatters.fmt2(r.bonusTotal())),
                new Label("最终分：" + Formatters.fmt2(r.finalScore())));
        box.setPadding(new Insets(16));
        box.setPrefWidth(420);
        Dialogs.custom("结束评分结果", box);
    }

    private void exitSession() {
        current = null;
        refresh();
    }

    // ------------------------------------------------------------------ Page

    @Override
    public Region view() {
        return root;
    }

    @Override
    public void refresh() {
        String requested = pendingNo;
        pendingNo = null;
        CandidateInfo interviewing;
        try {
            interviewing = ctx.svc().currentInterviewing();
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
            return;
        }
        if (interviewing != null) {
            // 有未完成面试（含跨进程恢复）：直接进入会话视图
            enterSession(interviewing);
            return;
        }
        if (requested != null) {
            beginFor(requested);
            if (current != null) {
                return;
            }
        }
        current = null;
        root.setCenter(buildPickView());
        doPickSearch();
    }
}
