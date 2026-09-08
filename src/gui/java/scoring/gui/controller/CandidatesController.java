package scoring.gui.controller;

import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import scoring.core.dto.CandidateInfo;
import scoring.core.dto.CandidateStatus;
import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;
import scoring.gui.ui.Formatters;

/**
 * 候选人列表页：常驻搜索框 + 名单表格（学号/姓名/状态徽标/完成时间），双击行进入明细。
 */
public final class CandidatesController implements Page {

    private final AppContext ctx;
    private final BorderPane root;
    private final TextField searchField;
    private final TableView<CandidateInfo> table;
    private final Label countLabel;
    private NameListCard nameCard;

    public CandidatesController(AppContext ctx) {
        this.ctx = ctx;
        this.root = new BorderPane();
        root.getStyleClass().add("page");
        root.setTop(buildHeader());

        searchField = new TextField();
        searchField.setPromptText("输入姓名（部分即可）或学号（可输前几位）；回车搜索，留空显示全部");
        searchField.setOnAction(e -> doSearch());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("搜索");
        searchBtn.getStyleClass().add("button-primary");
        searchBtn.setOnAction(e -> doSearch());
        Button allBtn = new Button("显示全部");
        allBtn.getStyleClass().add("button-secondary");
        allBtn.setOnAction(e -> {
            searchField.clear();
            doSearch();
        });

        HBox searchBar = new HBox(10, searchField, searchBtn, allBtn);
        searchBar.getStyleClass().add("search-bar");

        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        countLabel = new Label();
        countLabel.getStyleClass().add("hint-text");

        // 名单管理操作组（二期）：新增/改名/改学号/删除；随表格选中行启用
        nameCard = new NameListCard(ctx);
        nameCard.onBind(this::doSearch,
                no -> doSearch(),
                () -> {
                    searchField.clear();
                    doSearch();
                });

        VBox body = new VBox(14, searchBar, table, countLabel, nameCard);
        body.setPadding(new Insets(24));
        root.setCenter(body);
    }

    private VBox buildHeader() {
        Label title = new Label("候选人列表");
        title.getStyleClass().add("page-title");
        Label sub = new Label("双击某一行进入该候选人的评分明细（补录/附加分）。");
        sub.getStyleClass().add("page-subtitle");
        VBox box = new VBox(4, title, sub);
        box.getStyleClass().add("page-header");
        return box;
    }

    @SuppressWarnings("unchecked")
    private TableView<CandidateInfo> buildTable() {
        TableView<CandidateInfo> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPlaceholder(new Label("暂无候选人，请先在「名单导入」页导入名单。"));

        TableColumn<CandidateInfo, String> noCol = new TableColumn<>("学号");
        noCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().studentNo()));

        TableColumn<CandidateInfo, String> nameCol = new TableColumn<>("姓名");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<CandidateInfo, CandidateStatus> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(c ->
                new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue().status()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();

            {
                badge.getStyleClass().add("badge");
            }

            @Override
            protected void updateItem(CandidateStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(status.label());
                badge.getStyleClass().removeAll("badge-pending", "badge-interviewing", "badge-finished");
                switch (status) {
                    case PENDING -> badge.getStyleClass().add("badge-pending");
                    case INTERVIEWING -> badge.getStyleClass().add("badge-interviewing");
                    case FINISHED -> badge.getStyleClass().add("badge-finished");
                }
                setGraphic(badge);
            }
        });

        TableColumn<CandidateInfo, String> timeCol = new TableColumn<>("完成时间");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(Formatters.fmtTime(c.getValue().finishedAt())));

        tv.getColumns().setAll(noCol, nameCol, statusCol, timeCol);

        // 选中行 → 名单管理组绑定目标（清空选择时置灰改名/改号/删除）
        tv.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (nameCard != null) {
                nameCard.bindTarget(n == null ? null : n.studentNo(),
                        n != null && n.status() == CandidateStatus.INTERVIEWING);
            }
        });

        tv.setRowFactory(v -> {
            TableRow<CandidateInfo> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    ctx.nav().showDetail(row.getItem().studentNo());
                }
            });
            return row;
        });
        return tv;
    }

    private void doSearch() {
        String kw = searchField.getText() == null ? "" : searchField.getText().trim();
        try {
            List<CandidateInfo> list = kw.isEmpty()
                    ? ctx.svc().listCandidates()
                    : ctx.svc().searchCandidates(kw);
            table.getItems().setAll(list);
            table.getSelectionModel().clearSelection();
            nameCard.bindTarget(null, false);
            countLabel.setText(kw.isEmpty()
                    ? "共 " + list.size() + " 人。"
                    : "关键字「" + kw + "」匹配到 " + list.size() + " 人（最多显示 20 条）。");
            if (!kw.isEmpty() && list.isEmpty()) {
                Dialogs.info("搜索", "没有匹配的候选人，请检查姓名/学号后重试。");
            }
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
        doSearch();
    }
}
