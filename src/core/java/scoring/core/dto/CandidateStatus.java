package scoring.core.dto;

/**
 * 候选人面试状态机（core 层唯一状态来源，随记录入库）。
 *
 * <p>流转规则（由 ScoringService 强制）：
 * <ul>
 *   <li>PENDING —— 已导入名单、尚未开始面试（初始化后所有候选人回到此状态）；</li>
 *   <li>INTERVIEWING —— 已进入面试流程、尚未「结束评分」（同一时刻全局最多一名，中途退出可续面）；</li>
 *   <li>FINISHED —— 面试完成（结束评分时写入完成时间），此后只能补录/附加，不能重开面试。</li>
 * </ul>
 */
public enum CandidateStatus {

    /** 未面试：已导入名单，尚未开始面试。 */
    PENDING("未面试"),

    /** 面试中：正在进行面试打分，同一时刻全局仅允许一名。 */
    INTERVIEWING("面试中"),

    /** 已结束面试：面试完成，最终分由评分明细动态计算。 */
    FINISHED("已结束面试");

    private final String label;

    CandidateStatus(String label) {
        this.label = label;
    }

    /** 面向用户的中文展示名。 */
    public String label() {
        return label;
    }

    /** 从数据库存储值还原枚举（用于内部 DAO）。 */
    public static CandidateStatus fromDb(String dbValue) {
        return CandidateStatus.valueOf(dbValue);
    }
}
