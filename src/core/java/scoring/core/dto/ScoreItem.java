package scoring.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条四维评分记录（面试打分、补录共用同一张表）。
 *
 * <p>四个维度（各 0～25、最多两位小数，由 core 校验）：
 * r=责任心；t=时间管理能力；s=学生工作能力；f=部门契合度。一条记录 = 一次完整四维录入。
 *
 * @param id        数据库主键（撤销/删除操作引用）
 * @param studentNo 所属候选人学号
 * @param r         责任心分值
 * @param t         时间管理能力分值
 * @param s         学生工作能力分值
 * @param f         部门契合度分值
 * @param addedAt   录入时间
 */
public record ScoreItem(
        long id,
        String studentNo,
        BigDecimal r,
        BigDecimal t,
        BigDecimal s,
        BigDecimal f,
        LocalDateTime addedAt) {
}
