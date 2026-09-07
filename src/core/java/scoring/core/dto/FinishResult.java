package scoring.core.dto;

import java.math.BigDecimal;

/**
 * 「结束评分」的执行结果（面试打分流程的 GUI 交互模型）。
 *
 * <p>两条使用路径：
 * <ol>
 *   <li>评分 ≥3 条：调用 {@code finishInterview(no, false)} 直接完成，{@code completed=true}；</li>
 *   <li>评分 1～2 条：先调用 {@code finishInterview(no, false)} 得到 {@code completed=false}、
 *       {@code belowThree=true} 的预览（message 说明将按普通平均计算），GUI 弹二次确认框，
 *       确认后再次调用 {@code finishInterview(no, true)} 完成；用户拒绝则继续留在面试中。</li>
 * </ol>
 *
 * @param completed   本次调用是否真正结束了面试（false 表示需要二次确认的预览）
 * @param belowThree  结束时普通评分是否不足 3 条
 * @param average     最终采用/将采用的平均分（去极值或普通平均）
 * @param avgMethod   平均分计算方法
 * @param bonusTotal  该候选人全部附加分合计
 * @param finalScore  最终分 = average + bonusTotal；仅 completed=true 时有值（已完成）
 * @param message     面向用户的中文说明（预览时的提示 / 完成时的结算摘要）
 */
public record FinishResult(
        boolean completed,
        boolean belowThree,
        BigDecimal average,
        AvgMethod avgMethod,
        BigDecimal bonusTotal,
        BigDecimal finalScore,
        String message) {
}
