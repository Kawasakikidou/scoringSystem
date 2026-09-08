package scoring.core.dto;

import java.math.BigDecimal;

/**
 * 「结束评分」的执行结果（面试打分流程的 GUI 交互模型）。
 *
 * <p>n = 四维评分记录条数（每条记录必含四个维度，故各维 n 相同）。
 * n ≥ 3：各维度分别去掉一个最高/一个最低后取平均（四维独立去极值）；
 * n = 1～2：各维度按普通平均计算（需二次确认）；n = 0 拒绝结束。
 *
 * @param completed      本次调用是否真正结束了面试（false 表示需要二次确认的预览）
 * @param belowThree     结束时评分记录是否不足 3 条
 * @param rAvg           责任心平均分
 * @param tAvg           时间管理能力平均分
 * @param sAvg           学生工作能力平均分
 * @param fAvg           部门契合度平均分
 * @param dimensionTotal 四维平均之和（= rAvg+tAvg+sAvg+fAvg，舍入到两位）
 * @param avgMethod      平均分计算方法（TRIMMED/PLAIN）
 * @param bonusTotal     该候选人全部附加分合计
 * @param finalScore     最终分 = dimensionTotal + bonusTotal；仅 completed=true 时有值
 * @param message        面向用户的中文说明（预览时的提示 / 完成时的结算摘要）
 */
public record FinishResult(
        boolean completed,
        boolean belowThree,
        BigDecimal rAvg,
        BigDecimal tAvg,
        BigDecimal sAvg,
        BigDecimal fAvg,
        BigDecimal dimensionTotal,
        AvgMethod avgMethod,
        BigDecimal bonusTotal,
        BigDecimal finalScore,
        String message) {
}
