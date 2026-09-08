package scoring.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排名表的一行（仅 FINISHED 候选人参与排名；未结束者在 GUI 中请用
 * {@code listCandidates()} 单独展示「未完成」区，不占用名次）。
 *
 * <p>排序规则：最终分降序 → 同分比较四维平均（责任心 → 时间管理能力 → 学生工作能力
 * → 部门契合度，逐维两位小数比较）→ 仍同分按学号升序（结果确定无并列）。
 *
 * @param rank             名次（从 1 开始，连续编号）
 * @param candidate        候选人基础信息
 * @param rAvg             责任心平均分
 * @param tAvg             时间管理能力平均分
 * @param sAvg             学生工作能力平均分
 * @param fAvg             部门契合度平均分
 * @param dimensionTotal   四维平均之和（≤100）
 * @param avgMethod        平均方法
 * @param bonusTotal       附加分合计（直接并入最终分，无总上限）
 * @param finalScore       最终分 = dimensionTotal + bonusTotal
 * @param normalScoreCount 四维评分记录条数
 * @param finishedAt       面试完成时间
 */
public record RankRow(
        int rank,
        CandidateInfo candidate,
        BigDecimal rAvg,
        BigDecimal tAvg,
        BigDecimal sAvg,
        BigDecimal fAvg,
        BigDecimal dimensionTotal,
        AvgMethod avgMethod,
        BigDecimal bonusTotal,
        BigDecimal finalScore,
        int normalScoreCount,
        LocalDateTime finishedAt) {
}
