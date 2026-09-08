package scoring.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条评分操作日志（撤销/还原功能的数据来源，随数据库持久化）。
 *
 * <p>被记录的操作类型（opType）：
 * ADD_SCORE（录入/补录四维评分）、DEL_SCORE（删除上一条评分，误输入）、ADD_BONUS（添加附加分）。
 * 日志按 seq 严格递增，撤销即 LIFO 弹出最后一条并反向执行。
 *
 * @param seq          日志序号（全局递增，撤销的锚点）
 * @param studentNo    被操作候选人学号
 * @param studentName  被操作候选人姓名（冗余，便于展示与 GUI 列表）
 * @param opType       操作类型：ADD_SCORE / DEL_SCORE / ADD_BONUS
 * @param r            评分快照：责任心（ADD_SCORE/DEL_SCORE 时使用）
 * @param t            评分快照：时间管理能力
 * @param s            评分快照：学生工作能力
 * @param f            评分快照：部门契合度
 * @param amount       附加分快照（ADD_BONUS 时使用）
 * @param reason       附加分原因快照（ADD_BONUS 时使用）
 * @param description  面向用户的中文描述（可直接展示）
 * @param time         操作时间
 */
public record OpLogEntry(
        long seq,
        String studentNo,
        String studentName,
        String opType,
        BigDecimal r,
        BigDecimal t,
        BigDecimal s,
        BigDecimal f,
        BigDecimal amount,
        String reason,
        String description,
        LocalDateTime time) {
}
