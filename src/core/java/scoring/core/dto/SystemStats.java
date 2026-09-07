package scoring.core.dto;

/**
 * 系统状态统计（CLI 欢迎页/初始化前的风险提示、GUI 仪表盘通用）。
 *
 * @param totalCandidates 名单候选人总数（导入保留，初始化不清空）
 * @param pending         未面试人数
 * @param interviewing    面试中人数（正常情况下为 0 或 1）
 * @param finished        已结束面试人数
 * @param scoreCount      全部普通评分条数
 * @param bonusCount      全部附加分记录条数
 */
public record SystemStats(
        int totalCandidates,
        int pending,
        int interviewing,
        int finished,
        int scoreCount,
        int bonusCount) {
}
