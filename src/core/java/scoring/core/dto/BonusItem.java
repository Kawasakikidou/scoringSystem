package scoring.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条附加分记录（如才艺加分）。
 *
 * <p>附加分直接计入最终分、不参与去极值平均；多笔附加分各自成行并求和，单独列示。
 *
 * @param id        数据库主键
 * @param studentNo 所属候选人学号
 * @param amount    附加分值，域 [0, 100]，最多两位小数
 * @param reason    加分原因（必填，去除首尾空白后不得为空，长度 ≤ 200 字）
 * @param addedAt   记录时间
 */
public record BonusItem(
        long id,
        String studentNo,
        BigDecimal amount,
        String reason,
        LocalDateTime addedAt) {
}
