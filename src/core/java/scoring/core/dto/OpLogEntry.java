package scoring.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条评分操作日志（撤销/还原功能的数据来源，随数据库持久化）。
 *
 * <p>被记录的操作类型（opType）：ADD_SCORE（录入/补录普通评分）、DEL_SCORE（删除上一条评分，误输入）、
 * ADD_BONUS（添加附加分）。日志按 seq 严格递增，撤销即 LIFO 弹出最后一条并反向执行。
 *
 * @param seq          日志序号（全局递增，撤销的锚点）
 * @param studentNo    被操作候选人学号
 * @param studentName  被操作候选人姓名（冗余，便于展示与 GUI 列表）
 * @param opType       操作类型：ADD_SCORE / DEL_SCORE / ADD_BONUS
 * @param value        本次操作的分值快照（撤销删除评分时用它原值恢复）
 * @param reason       附加分原因快照（ADD_BONUS 时使用）
 * @param description  面向用户的中文描述（可直接展示）
 * @param time         操作时间
 */
public record OpLogEntry(
        long seq,
        String studentNo,
        String studentName,
        String opType,
        BigDecimal value,
        String reason,
        String description,
        LocalDateTime time) {
}
