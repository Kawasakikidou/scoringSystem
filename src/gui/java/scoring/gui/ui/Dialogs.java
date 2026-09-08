package scoring.gui.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * 通用对话框封装：信息 / 错误 / 确认 / 自定义内容 / YES 二次确认。
 * 业务错误一律把 core 抛出的中文 message 直接展示给用户。
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** 成功/提示信息。 */
    public static void info(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.showAndWait();
    }

    /** 业务/运行错误（标题统一为「操作未完成」，与 CLI 口径一致）。 */
    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("操作未完成");
        a.setHeaderText("操作未完成");
        a.setContentText(message);
        a.showAndWait();
    }

    /** 是/否确认；返回 true 表示用户点了「确认」。 */
    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        return a.showAndWait().filter(bt -> bt == ButtonType.OK).isPresent();
    }

    /** 展示自定义内容的对话框（用于导入报告、结算结果、撤销结果等）。 */
    public static void custom(String title, Node content) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    /**
     * 三选一确认（如初始化前「先备份 / 跳过备份 / 取消」）。
     *
     * @return 用户点中的按钮在 options 中的下标；关闭对话框返回 -1
     */
    public static int choose(String title, String message, String... options) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        ButtonType[] types = new ButtonType[options.length];
        for (int i = 0; i < options.length; i++) {
            types[i] = new ButtonType(options[i],
                    i == options.length - 1 ? ButtonBar.ButtonData.CANCEL_CLOSE : ButtonBar.ButtonData.OTHER);
        }
        a.getButtonTypes().setAll(types);
        ButtonType picked = a.showAndWait().orElse(null);
        for (int i = 0; i < types.length; i++) {
            if (types[i] == picked) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 「输入 YES 才放行」的二次确认对话框（初始化系统用）。
     *
     * @return true 表示用户输入了 YES（不区分大小写）并点击确认
     */
    public static boolean confirmTypedYes(String title, String message) {
        return confirmTyped(title, message, "YES", "确认初始化");
    }

    /**
     * 「手输指定确认词才放行」的二次确认对话框（彻底重置等高危操作用）。
     *
     * @param expectedWord 要求手输的确认词（不区分大小写，如 YES / 彻底重置）
     * @param okText       确认按钮文字
     * @return true 表示用户输入了确认词并点击确认按钮
     */
    public static boolean confirmTyped(String title, String message,
                                       String expectedWord, String okText) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle(title);
        ButtonType okType = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Label msg = new Label(message);
        msg.setWrapText(true);
        TextField field = new TextField();
        field.setPromptText("请输入 " + expectedWord);
        VBox box = new VBox(12, msg, new Label("如确需执行，请在下方输入 " + expectedWord
                + "（其它输入均视为取消）："), field);
        box.setPadding(new Insets(16));
        box.setPrefWidth(460);
        dlg.getDialogPane().setContent(box);

        Node okBtn = dlg.getDialogPane().lookupButton(okType);
        okBtn.setDisable(true);
        field.textProperty().addListener((obs, o, n) ->
                okBtn.setDisable(!expectedWord.equalsIgnoreCase(n.trim())));
        dlg.setResultConverter(bt -> bt == okType);
        return dlg.showAndWait().orElse(false);
    }
}
