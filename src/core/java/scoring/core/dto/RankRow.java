package scoring.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排名表的一行（仅 FINISHED 候选人参与排名；未结束者在 GUI 中请用
 * {@code listCandidates()} 单独展示「未完成」区，不占用名次）。
 *
 * <p>排序规则：最终分降序；最终分相同按学号升序（稳定，结果确定无并列）。
 *
 * @param rank             名次（从 1 开始，连续编号）
 * @param candidate        候选人基础信息
 * @param average          平均分（去极值或普通平均）
 * @param avgMethod        平均方法
 * @param bonusTotal       附加分合计（不计入去极值，直接并入最终分）
 * @param finalScore       最终分
 * @param normalScoreCount 普通评分条数
 * @param finishedAt       面试完成时间
 */
public record RankRow(
        int rank,
        CandidateInfo candidate,
        BigDecimal average,
        AvgMethod avgMethod,
        BigDecimal bonusTotal,
        BigDecimal finalScore,
        int normalScoreCount,
        LocalDateTime finishedAt) {
}
