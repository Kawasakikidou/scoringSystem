package scoring.gui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import scoring.core.dto.CandidateInfo;
import scoring.core.dto.SystemStats;
import scoring.gui.AppContext;

/**
 * 仪表盘：系统状态统计卡片 + 未完成面试提示（恢复入口）+ 常用功能快捷入口。
 */
public final class DashboardController implements Page {

    private final AppContext ctx;
    private final BorderPane root;

    private Label totalVal;
    private Label pendingVal;
    private Label interviewingVal;
    private Label finishedVal;
    private Label scoreCountVal;
    private Label bonusCountVal;

    private VBox resumeCard;
    private Label resumeText;
    private Button resumeBtn;

    private CandidateInfo interviewing;

    public DashboardController(AppContext ctx) {
        this.ctx = ctx;
        this.root = new BorderPane();
        root.getStyleClass().add("page");
        root.setTop(buildHeader());
        root.setCenter(buildBody());
    }

    private VBox buildHeader() {
        Label title = new Label("仪表盘");
        title.getStyleClass().add("page-title");
        Label sub = new Label("系统状态一览；每次回到本页会自动刷新统计。");
        sub.getStyleClass().add("page-subtitle");
        VBox box = new VBox(4, title, sub);
        box.getStyleClass().add("page-header");
        return box;
    }

    private VBox buildBody() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        totalVal = statValue();
        pendingVal = statValue();
        interviewingVal = statValue();
        finishedVal = statValue();
        scoreCountVal = statValue();
        bonusCountVal = statValue();

        grid.add(statCard("名单总人数", totalVal, "已导入的候选人总数", "stat-total"), 0, 0);
        grid.add(statCard("未面试", pendingVal, "等待开始面试", "stat-pending"), 1, 0);
        grid.add(statCard("面试中", interviewingVal, "同一时刻最多 1 人", "stat-interviewing"), 2, 0);
        grid.add(statCard("已结束面试", finishedVal, "已产生最终分", "stat-finished"), 0, 1);
        grid.add(statCard("普通评分条数", scoreCountVal, "全部已录入评分", "stat-total"), 1, 1);
        grid.add(statCard("附加分条数", bonusCountVal, "才艺等加分记录", "stat-total"), 2, 1);

        resumeCard = new VBox(8);
        resumeCard.getStyleClass().addAll("card", "resume-card");
        resumeText = new Label();
        resumeText.getStyleClass().add("resume-text");
        resumeText.setWrapText(true);
        resumeBtn = new Button("继续该面试 →");
        resumeBtn.getStyleClass().add("button-primary");
        resumeBtn.setOnAction(e -> {
            if (interviewing != null) {
                ctx.nav().showInterview(interviewing.studentNo());
            }
        });
        resumeCard.getChildren().addAll(resumeText, resumeBtn);

        Label quickTitle = new Label("常用操作");
        quickTitle.getStyleClass().add("card-title");
        Button goImport = quickBtn("导入名单", "import");
        Button goCandidates = quickBtn("候选人列表", "candidates");
        Button goInterview = quickBtn("面试打分", "interview");
        Button goRanking = quickBtn("查看排名", "ranking");
        HBox quick = new HBox(12, goImport, goCandidates, goInterview, goRanking);
        quick.setAlignment(Pos.CENTER_LEFT);
        VBox quickCard = new VBox(10, quickTitle, quick);
        quickCard.getStyleClass().add("card");

        VBox body = new VBox(20, grid, resumeCard, quickCard);
        body.setPadding(new Insets(24));
        return body;
    }

    private Button quickBtn(String text, String pageId) {
        Button b = new Button(text);
        b.getStyleClass().add("button-secondary");
        b.setOnAction(e -> ctx.nav().show(pageId));
        return b;
    }

    private Label statValue() {
        Label l = new Label("0");
        l.getStyleClass().add("stat-value");
        return l;
    }

    private VBox statCard(String title, Label value, String hint, String style) {
        Label t = new Label(title);
        t.getStyleClass().add("stat-title");
        Label h = new Label(hint);
        h.getStyleClass().add("stat-hint");
        VBox card = new VBox(6, t, value, h);
        card.getStyleClass().addAll("card", "stat-card", style);
        card.setPrefWidth(260);
        return card;
    }

    @Override
    public Region view() {
        return root;
    }

    @Override
    public void refresh() {
        try {
            SystemStats st = ctx.svc().stats();
            totalVal.setText(String.valueOf(st.totalCandidates()));
            pendingVal.setText(String.valueOf(st.pending()));
            interviewingVal.setText(String.valueOf(st.interviewing()));
            finishedVal.setText(String.valueOf(st.finished()));
            scoreCountVal.setText(String.valueOf(st.scoreCount()));
            bonusCountVal.setText(String.valueOf(st.bonusCount()));

            interviewing = ctx.svc().currentInterviewing();
            if (interviewing != null) {
                resumeText.setText("⚠ 有未完成的面试：" + interviewing.name()
                        + "（" + interviewing.studentNo() + "）。可继续打分，或结束评分。");
                resumeCard.setVisible(true);
                resumeCard.setManaged(true);
            } else {
                resumeCard.setVisible(false);
                resumeCard.setManaged(false);
            }
        } catch (RuntimeException e) {
            scoring.gui.ui.Dialogs.error(e.getMessage());
        }
    }
}
