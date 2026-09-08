package scoring.gui;

import scoring.core.ScoringService;

/**
 * GUI 共享上下文：持有 core 层服务单例与页面导航器。
 * 每个窗口一个实例；所有业务调用都经 {@link #svc()} 走 core 公开 API。
 */
public final class AppContext {

    /** 页面导航（由 Main 实现）。 */
    public interface Navigator {
        /** 切换到侧栏页面：dashboard / import / candidates / interview / ranking / undo。 */
        void show(String pageId);

        /** 打开候选人明细页。 */
        void showDetail(String studentNo);

        /** 打开面试打分页并指定候选人（进入会话或预选）。 */
        void showInterview(String studentNo);
    }

    private final ScoringService service;
    private final Navigator navigator;

    public AppContext(ScoringService service, Navigator navigator) {
        this.service = service;
        this.navigator = navigator;
    }

    public ScoringService svc() {
        return service;
    }

    public Navigator nav() {
        return navigator;
    }
}
