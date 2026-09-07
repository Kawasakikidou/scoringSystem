package scoring.core.dto;

import java.math.BigDecimal;

/**
 * 「撤销最近一次评分操作」的结果。
 *
 * @param studentNo        被撤销操作所属候选人学号
 * @param studentName      被撤销操作所属候选人姓名
 * @param description      已撤销操作的中文描述（如：已撤销「添加普通评分 85.50」）
 * @param candidateFinished 该候选人是否处于已结束面试状态（true 时 finalScore 有值）
 * @param finalScore       撤销重算后的最新最终分；候选人未结束时为 null
 * @param normalCount      撤销后该候选人剩余的普通评分条数（便于 UI 提示）
 */
public record UndoResult(
        String studentNo,
        String studentName,
        String description,
        boolean candidateFinished,
        BigDecimal finalScore,
        int normalCount) {
}
