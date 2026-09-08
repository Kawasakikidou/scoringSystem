package scoring.gui.ui;

import java.math.BigDecimal;

import scoring.core.ScoringService;

/**
 * 分值输入框的即时校验（仅输入校验，口径与服务端一致：维度 0~25、附加分 0~10、
 * 均最多两位小数；业务规则的最终裁决仍在 core 层）。
 */
public final class ScoreInputValidator {

    private ScoreInputValidator() {
    }

    /**
     * 校验单个维度的输入文本（0~{@link ScoringService#DIM_MAX}，最多两位小数）。
     *
     * @return 错误提示（中文）；合法时返回 null
     */
    public static String validateDim(String text, String dimLabel) {
        return validateRange(text, dimLabel + "分值", ScoringService.DIM_MAX);
    }

    /** 校验附加分输入文本（0~{@link ScoringService#BONUS_MAX}）。 */
    public static String validateBonus(String text) {
        return validateRange(text, "附加分值", ScoringService.BONUS_MAX);
    }

    private static String validateRange(String text, String what, BigDecimal max) {
        if (text == null || text.trim().isEmpty()) {
            return "请输入" + what + "。";
        }
        BigDecimal v;
        try {
            v = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return "无法识别数字，请输入 0~" + max.toPlainString() + " 之间的数（如 20 或 20.5）。";
        }
        if (v.stripTrailingZeros().scale() > 2) {
            return what + "最多支持两位小数。";
        }
        if (v.compareTo(ScoringService.MIN_VALUE) < 0 || v.compareTo(max) > 0) {
            return what + "需在 0~" + max.toPlainString() + " 之间。";
        }
        return null;
    }

    /** 解析输入文本（调用前请先校验通过）。 */
    public static BigDecimal parse(String text) {
        return new BigDecimal(text.trim());
    }
}
