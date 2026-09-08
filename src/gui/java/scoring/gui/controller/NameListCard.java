package scoring.gui.controller;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

import scoring.gui.AppContext;
import scoring.gui.ui.Dialogs;

/**
 * 名单管理操作组（二期）：新增候选人 / 改名 / 改学号 / 删除。
 * 可复用于候选人列表页与候选人明细页：
 * 通过 {@link #bindTarget} 绑定当前操作的候选人（null = 未选中，改名/改号/删除置灰）；
 * 面试中的候选人改号/删除由 core 拒绝，GUI 额外前置置灰。
 * 删除/改号均弹确认对话框并提示级联影响（评分记录/附加分/操作日志一并删除或迁移）。
 */
public final class NameListCard extends VBox {

    private final AppContext ctx;
    private Runnable onRefresh;
    private Consumer<String> onStudentNoChanged;
    private Runnable onDeleted;

    private TextField addNameField;
    private TextField addNoField;
    private TextField renameField;
    private TextField renoField;
    private Button renameBtn;
    private Button renoBtn;
    private Button deleteBtn;
    private final Label status = new Label();

    /** 当前绑定的候选人学号；null = 未选中。 */
    private String targetNo;

    public NameListCard(AppContext ctx) {
        this.ctx = ctx;
        buildUi();
    }

    /** 注册回调：变更后刷新列表/明细；改号后跳转到新学号；删除后离开当前视图。 */
    public NameListCard onBind(Runnable onRefresh, Consumer<String> onStudentNoChanged,
                               Runnable onDeleted) {
        this.onRefresh = onRefresh;
        this.onStudentNoChanged = onStudentNoChanged;
        this.onDeleted = onDeleted;
        return this;
    }

    /** 绑定当前候选人；studentNo 为 null 表示未选中。 */
    public void bindTarget(String studentNo, boolean interviewing) {
        this.targetNo = studentNo;
        boolean has = studentNo != null && !studentNo.isBlank();
        renameBtn.setDisable(!has);
        renoBtn.setDisable(!has || interviewing);
        deleteBtn.setDisable(!has || interviewing);
        if (!has) {
            status.setText("");
        }
    }

    private void buildUi() {
        Label title = new Label("名单管理");
        title.getStyleClass().add("card-title");

        addNameField = new TextField();
        addNameField.setPromptText("姓名（2~64 个汉字，· 连接）");
        addNoField = new TextField();
        addNoField.setPromptText("学号（10 位数字）");
        Button addBtn = new Button("新增候选人");
        addBtn.getStyleClass().add("button-primary");
        addBtn.setOnAction(e -> doAdd());
        HBox addRow = new HBox(8, addNameField, addNoField, addBtn);

        renameField = new TextField();
        renameField.setPromptText("输入新姓名后改名");
        renameBtn = new Button("改名");
        renameBtn.getStyleClass().add("button-secondary");
        renameBtn.setOnAction(e -> doRename());
        HBox renameRow = new HBox(8, renameField, renameBtn);

        renoField = new TextField();
        renoField.setPromptText("输入新学号（评分/附加/日志随之迁移）");
        renoBtn = new Button("改学号");
        renoBtn.getStyleClass().add("button-secondary");
        renoBtn.setOnAction(e -> doRenumber());
        HBox renoRow = new HBox(8, renoField, renoBtn);

        deleteBtn = new Button("删除该候选人（级联删除其评分/附加/日志）");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> doDelete());

        status.getStyleClass().add("status-ok");
        status.setWrapText(true);

        setSpacing(8);
        setPadding(new Insets(14));
        getStyleClass().add("card");
        getChildren().setAll(title, addRow, renameRow, renoRow, deleteBtn, status);
    }

    private void doAdd() {
        try {
            ctx.svc().addCandidate(addNameField.getText(), addNoField.getText());
            status.setText("✓ 已新增候选人 " + addNameField.getText().trim()
                    + "（" + addNoField.getText().trim() + "）。");
            addNameField.clear();
            addNoField.clear();
            if (onRefresh != null) {
                onRefresh.run();
            }
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doRename() {
        String no = targetNo;
        if (no == null) {
            return;
        }
        String newName = renameField.getText();
        if (newName == null || newName.trim().isEmpty()) {
            Dialogs.error("请先在左侧输入框填写新姓名。");
            return;
        }
        try {
            ctx.svc().updateCandidateName(no, newName);
            status.setText("✓ 已改名（" + no + "）。");
            renameField.clear();
            if (onRefresh != null) {
                onRefresh.run();
            }
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doRenumber() {
        String no = targetNo;
        if (no == null) {
            return;
        }
        String newNo = renoField.getText() == null ? "" : renoField.getText().trim();
        if (newNo.isEmpty()) {
            Dialogs.error("请先在左侧输入框填写新学号。");
            return;
        }
        if (!Dialogs.confirm("确认改学号",
                "将把学号 " + no + " 改为 " + newNo + "。\n\n"
                        + "该候选人的全部四维评分、附加分与操作日志会一并迁移到新学号；"
                        + "面试中的候选人不可改号。是否继续？")) {
            return;
        }
        try {
            ctx.svc().updateCandidateStudentNo(no, newNo);
            status.setText("✓ 已改学号：" + no + " → " + newNo + "。");
            renoField.clear();
            if (onStudentNoChanged != null) {
                onStudentNoChanged.accept(newNo);
            } else if (onRefresh != null) {
                onRefresh.run();
            }
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void doDelete() {
        String no = targetNo;
        if (no == null) {
            return;
        }
        if (!Dialogs.confirm("确认删除候选人",
                "将删除候选人 " + no + "，并级联删除其全部四维评分记录、附加分与相关操作日志，"
                        + "删除后该学号可重新添加。此操作不可撤销，是否继续？")) {
            return;
        }
        try {
            ctx.svc().deleteCandidate(no);
            status.setText("✓ 已删除候选人 " + no + "。");
            if (onDeleted != null) {
                onDeleted.run();
            } else if (onRefresh != null) {
                onRefresh.run();
            }
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }
}
