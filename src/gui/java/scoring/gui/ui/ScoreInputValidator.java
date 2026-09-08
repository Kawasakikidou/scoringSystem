package scoring.gui.ui;

import java.math.BigDecimal;

import scoring.core.ScoringService;

/**
 * 分值输入框的即时校验（仅输入校验，与服务端 0~100、最多两位小数的规则口径一致，
 * 业务规则的最终裁决仍在 core 层）。
 */
public final class ScoreInputValidator {

    private ScoreInputValidator() {
    }

    /**
     * 校验输入文本，返回错误提示（中文）；合法时返回 null。
     */
    public static String validate(String text, String what) {
        if (text == null || text.trim().isEmpty()) {
            return "请输入" + what + "。";
        }
        BigDecimal v;
        try {
            v = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return "无法识别数字，请输入 0~100 之间的数（如 85 或 85.5）。";
        }
        if (v.stripTrailingZeros().scale() > 2) {
            return what + "最多支持两位小数。";
        }
        if (v.compareTo(ScoringService.MIN_VALUE) < 0 || v.compareTo(ScoringService.MAX_VALUE) > 0) {
            return what + "需在 0~100 之间。";
        }
        return null;
    }

    /** 解析输入文本（调用前请先 {@link #validate} 通过）。 */
    public static BigDecimal parse(String text) {
        return new BigDecimal(text.trim());
    }
}
