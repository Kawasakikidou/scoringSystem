package scoring.gui.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GUI 展示格式化工具（仅做显示排版，不含任何业务规则）。
 */
public final class Formatters {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 占位符：数值/时间缺失（如未结束面试）时的展示。 */
    public static final String PLACEHOLDER = "—";

    private Formatters() {
    }

    /** 两位小数字符串；null（未定分）时返回占位符。 */
    public static String fmt2(BigDecimal v) {
        return v == null ? PLACEHOLDER : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 时间字符串；null 时返回占位符。 */
    public static String fmtTime(LocalDateTime t) {
        return t == null ? PLACEHOLDER : TIME_FMT.format(t);
    }
}
