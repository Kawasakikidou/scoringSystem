package scoring.core.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 候选人评分明细（查看明细 / 导出 CSV 明细列的数据来源）。
 *
 * <p>平均分与最终分不落库，全部由本对象按明细动态计算，保证与明细永远一致。
 *
 * @param candidate  候选人基础信息
 * @param scores     普通评分明细（按录入先后排序）
 * @param bonuses    附加分明细（按录入先后排序）
 * @param average    当前按规则计算的平均分（FINISHED 且 ≥3 条为去极值平均；否则按不足 3 条口径的普通平均）
 * @param avgMethod  本次计算使用的平均方法
 * @param bonusTotal 附加分合计
 * @param finalScore 当前最终分；候选人未结束面试（状态未 FINISHED）时为 null，
 *                   表示「尚未定分」——GUI/CLI 应据此显示占位符而非数值
 */
public record CandidateDetail(
        CandidateInfo candidate,
        List<ScoreItem> scores,
        List<BonusItem> bonuses,
        BigDecimal average,
        AvgMethod avgMethod,
        BigDecimal bonusTotal,
        BigDecimal finalScore) {
}
