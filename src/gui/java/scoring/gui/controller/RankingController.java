package scoring.gui.controller;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import scoring.core.ScoringService;
import scoring.core.dto.CandidateInfo;
import scoring.core.dto.CandidateStatus;
import scoring.core.dto.RankRow;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;
import scoring.gui.ui.Formatters;

/**
 * 排名页：ranking() 表格（仅已结束面试）+ 未完成候选人区域 + 导出 CSV。
 */
public final class RankingController implements Page {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AppContext ctx;
    private final BorderPane root;
    private TableView<RankRow> rankTable;
    private TableView<CandidateInfo> unfinishedTable;
    private Label unfinishedLabel;
    private Label ruleLabel;

    public RankingController(AppContext ctx) {
        this.ctx = ctx;
        this.root = new BorderPane();
        root.getStyleClass().add("page");
        root.setTop(buildHeader());
        root.setCenter(buildBody());
    }

    private VBox buildHeader() {
        Label title = new Label("排名");
        Label sub = new Label("仅「已结束面试」参与排名；排序规则：最终分降序 → 同分逐维比较（责任心→时间管理→学生工作→部门契合）→ 学号升序。");
        sub.getStyleClass().add("page-subtitle");
        VBox box = new VBox(4, title, sub);
        box.getStyleClass().add("page-header");
        return box;
    }

    @SuppressWarnings("unchecked")
    private VBox buildBody() {
        rankTable = new TableView<>();
        rankTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        rankTable.setPlaceholder(new Label("还没有已结束面试的候选人，暂无法生成排名。"));
        TableColumn<RankRow, String> rankCol = new TableColumn<>("名次");
        rankCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().rank())));
        TableColumn<RankRow, String> nameCol = new TableColumn<>("姓名");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().candidate().name()));
        TableColumn<RankRow, String> noCol = new TableColumn<>("学号");
        noCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().candidate().studentNo()));
        TableColumn<RankRow, String> rCol = new TableColumn<>(ScoringService.DIM_LABELS[0] + "均");
        rCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().rAvg())));
        TableColumn<RankRow, String> tCol = new TableColumn<>(ScoringService.DIM_LABELS[1] + "均");
        tCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().tAvg())));
        TableColumn<RankRow, String> sCol = new TableColumn<>(ScoringService.DIM_LABELS[2] + "均");
        sCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().sAvg())));
        TableColumn<RankRow, String> fCol = new TableColumn<>(ScoringService.DIM_LABELS[3] + "均");
        fCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().fAvg())));
        TableColumn<RankRow, String> dimTotalCol = new TableColumn<>("四维合计");
        dimTotalCol.setCellValueFactory(c ->
                new SimpleStringProperty(Formatters.fmt2(c.getValue().dimensionTotal())));
        TableColumn<RankRow, String> methodCol = new TableColumn<>("平均方式");
        methodCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().avgMethod().label()));
        TableColumn<RankRow, String> bonusCol = new TableColumn<>("附加合计");
        bonusCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().bonusTotal())));
        TableColumn<RankRow, String> finalCol = new TableColumn<>("最终分");
        finalCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmt2(c.getValue().finalScore())));
        TableColumn<RankRow, String> countCol = new TableColumn<>("记录数");
        countCol.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().normalScoreCount())));
        rankTable.getColumns().setAll(rankCol, nameCol, noCol, rCol, tCol, sCol, fCol,
                dimTotalCol, methodCol, bonusCol, finalCol, countCol);
        VBox.setVgrow(rankTable, Priority.ALWAYS);
        rankTable.setRowFactory(v -> {
            TableRow<RankRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    ctx.nav().showDetail(row.getItem().candidate().studentNo());
                }
            });
            return row;
        });

        Label rankTitle = new Label("已结束面试排名（双击行查看明细）");
        rankTitle.getStyleClass().add("card-title");
        ruleLabel = new Label();
        ruleLabel.getStyleClass().add("hint-text");
        VBox rankCard = new VBox(8, rankTitle, rankTable, ruleLabel);
        rankCard.getStyleClass().add("card");
        VBox.setVgrow(rankCard, Priority.ALWAYS);
        VBox.setVgrow(rankTable, Priority.ALWAYS);

        // 未完成区域
        unfinishedTable = new TableView<>();
        unfinishedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        unfinishedTable.setPlaceholder(new Label("全部候选人均已完成面试。"));
        TableColumn<CandidateInfo, String> uNoCol = new TableColumn<>("学号");
        uNoCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().studentNo()));
        TableColumn<CandidateInfo, String> uNameCol = new TableColumn<>("姓名");
        uNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        TableColumn<CandidateInfo, String> uStatusCol = new TableColumn<>("状态");
        uStatusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().label()));
        unfinishedTable.getColumns().setAll(uNoCol, uNameCol, uStatusCol);
        unfinishedTable.setPrefHeight(150);

        unfinishedLabel = new Label();
        unfinishedLabel.getStyleClass().add("card-title");
        VBox unfinishedCard = new VBox(8, unfinishedLabel, unfinishedTable);
        unfinishedCard.getStyleClass().add("card");

        Button exportBtn = new Button("导出 CSV…");
        exportBtn.getStyleClass().add("button-primary");
        exportBtn.setOnAction(e -> doExport());
        HBox actions = new HBox(exportBtn);
        actions.setPadding(new Insets(0, 0, 4, 0));

        VBox body = new VBox(16, actions, rankCard, unfinishedCard);
        body.setPadding(new Insets(24));
        VBox.setVgrow(rankCard, Priority.ALWAYS);
        return body;
    }

    private void doExport() {
        int scope = Dialogs.choose("导出 CSV", "请选择导出范围：\n\n"
                + "・仅已完成：正式排名结果\n"
                + "・全部：含未完成者（名次/分数留空、明细保留，适合备份）", "仅已完成", "全部（含未完成）", "取消");
        if (scope < 0 || scope == 2) {
            return;
        }
        boolean includeUnfinished = scope == 1;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存 CSV 文件");
        chooser.setInitialFileName("导出_" + LocalDateTime.now().format(FILE_TS) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV 文件", "*.csv"));
        File dataDir = new File("data");
        if (dataDir.isDirectory()) {
            chooser.setInitialDirectory(dataDir.getAbsoluteFile());
        }
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            int n = ctx.svc().exportCsv(file.toPath(), includeUnfinished);
            Dialogs.info("导出成功", "已导出 " + n + " 行数据 → " + file.getAbsolutePath()
                    + "\n\n文件为 UTF-8 带 BOM 编码，用 Excel 双击打开不会乱码。");
        } catch (IOException e) {
            Dialogs.error("导出失败：" + e.getMessage());
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
        try {
            List<RankRow> rows = ctx.svc().ranking();
            rankTable.getItems().setAll(rows);
            List<CandidateInfo> unfinished = new ArrayList<>();
            for (CandidateInfo c : ctx.svc().listCandidates()) {
                if (c.status() != CandidateStatus.FINISHED) {
                    unfinished.add(c);
                }
            }
            ruleLabel.setText("排序规则：最终分降序 → 同分按四维平均逐维比较（责任心→时间管理→学生工作→部门契合）→ 学号升序（无并列）。共 " + rows.size() + " 人参与排名。");
            ruleLabel.setText("排序规则：最终分降序；同分按学号升序。共 " + rows.size() + " 人参与排名。");
            unfinishedLabel.setText("未完成面试（" + unfinished.size() + " 人，不参与排名）");
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }
}
