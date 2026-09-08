package scoring.gui.controller;

import javafx.scene.layout.Region;

/**
 * 一个主内容页：view() 返回页面根节点（只创建一次），refresh() 在每次切入页面时调用，
 * 从 core 重新拉取数据（最终分等为动态计算值，务必每次刷新）。
 */
public interface Page {

    Region view();

    /** 切入页面时调用：重新加载数据并刷新视图。 */
    void refresh();
}
