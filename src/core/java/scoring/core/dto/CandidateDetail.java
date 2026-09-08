package scoring.core.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 候选人评分明细（查看明细 / 导出 CSV 明细列的数据来源）。
 *
 * <p>四维平均与最终分不落库，全部由本对象按明细动态计算，保证与明细永远一致。
 *
 * @param candidate      候选人基础信息
 * @param scores         四维评分记录明细（按录入先后排序）
 * @param bonuses        附加分明细（按录入先后排序）
 * @param rAvg           责任心平均分（维度内去极值或普通平均）
 * @param tAvg           时间管理能力平均分
 * @param sAvg           学生工作能力平均分
 * @param fAvg           部门契合度平均分
 * @param dimensionTotal 四维平均之和（每维 ≤25 → 合计 ≤100）
 * @param avgMethod      本次计算使用的平均方法（四维统一口径，见 AvgMethod）
 * @param bonusTotal     附加分合计
 * @param finalScore     当前最终分 = dimensionTotal + bonusTotal；候选人未结束面试时为 null
 */
public record CandidateDetail(
        CandidateInfo candidate,
        List<ScoreItem> scores,
        List<BonusItem> bonuses,
        BigDecimal rAvg,
        BigDecimal tAvg,
        BigDecimal sAvg,
        BigDecimal fAvg,
        BigDecimal dimensionTotal,
        AvgMethod avgMethod,
        BigDecimal bonusTotal,
        BigDecimal finalScore) {
}
