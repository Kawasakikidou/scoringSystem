package scoring.core.dto;

/**
 * 平均分计算方式：最终分公式中「去极值平均」或「普通平均」部分采用哪种方法。
 *
 * <ul>
 *   <li>TRIMMED：去掉一个最高分、一个最低分（并列时只各去掉一条）后取平均，仅当普通评分 ≥3 条时使用；</li>
 *   <li>PLAIN：评分不足 3 条（1～2 条）、经用户二次确认后按普通平均计算。</li>
 * </ul>
 *
 * 最终分 = 平均分(两种方法之一) + Σ附加分。
 */
public enum AvgMethod {

    /** 去极值平均：去掉 1 个最高分与 1 个最低分后的平均。 */
    TRIMMED("去极值平均"),

    /** 普通平均：评分不足 3 条时（经二次确认后）使用的兜底方式。 */
    PLAIN("普通平均");

    private final String label;

    AvgMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
