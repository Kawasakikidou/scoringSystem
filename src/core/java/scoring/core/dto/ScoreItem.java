package scoring.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条普通评分记录（面试打分、补录共用同一张表）。
 *
 * @param id        数据库主键（撤销/删除操作引用）
 * @param studentNo 所属候选人学号
 * @param value     分值，域 [0, 100]，最多两位小数
 * @param addedAt   录入时间
 */
public record ScoreItem(
        long id,
        String studentNo,
        BigDecimal value,
        LocalDateTime addedAt) {
}
