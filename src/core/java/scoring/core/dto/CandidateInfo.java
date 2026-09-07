package scoring.core.dto;

import java.time.LocalDateTime;

/**
 * 候选人基础信息（名单/搜索/状态列表使用，GUI 可直接展示）。
 *
 * @param studentNo  学号：恰好 10 位数字，主键/去重键
 * @param name       姓名（导入时按最后一条记录更新）
 * @param status     面试状态
 * @param finishedAt 面试完成时间；未结束时为 null
 */
public record CandidateInfo(
        String studentNo,
        String name,
        CandidateStatus status,
        LocalDateTime finishedAt) {
}
