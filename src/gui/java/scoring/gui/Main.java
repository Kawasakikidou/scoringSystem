package scoring.gui;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import scoring.core.ScoringService;
import scoring.core.dto.CandidateInfo;
import scoring.gui.controller.CandidatesController;
import scoring.gui.controller.DashboardController;
import scoring.gui.controller.DetailController;
import scoring.gui.controller.ImportController;
import scoring.gui.controller.InterviewController;
import scoring.gui.controller.Page;
import scoring.gui.controller.RankingController;
import scoring.gui.controller.ResetFlow;
import scoring.gui.controller.UndoController;
import scoring.gui.ui.Dialogs;

/**
 * GUI 入口：学生组织面试评分系统（JavaFX 17）。
 *
 * <p>本层只做界面与交互，全部业务操作经 {@link ScoringService} 完成；
 * 不依赖 System.in / System.out（无黑框 exe 打包前提）。
 *
 * <p>数据库位置解析顺序：系统属性 {@code scoring.db.url} → 环境变量 {@code SCORING_DB_URL}
 * → 默认分支 {@link #defaultDbUrl()}：优先「启动目录/data/」，若不可写（如打包安装在
 * Program Files 等受限目录）自动回退「程序所在目录/data/」→「用户主目录 .scoring-gui/data/」。
 * 注意：H2 2.2.224 拒绝「隐式相对路径」的 URL（如 file:data/scoring），故默认分支一律先转绝对路径；
 * 显式传入的 prop/env URL 原样透传，不做改写。
 */
public final class Main extends Application implements AppContext.Navigator {

    /** 默认数据库目录（相对启动目录）；拼接 URL 时转为绝对路径（见 {@link #resolveDbUrl()}）。 */
    public static final String DEFAULT_DB_PATH = "data/scoring";
    public static final String DEFAULT_DB_URL = "jdbc:h2:file:" + defaultDbAbsolute();

    private static String defaultDbAbsolute() {
        return new File(DEFAULT_DB_PATH).getAbsolutePath().replace('\\', '/');
    }

    private ScoringService service;
    private AppContext ctx;
    private Stage stage;

    private BorderPane root;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, ToggleButton> navButtons = new LinkedHashMap<>();
    private ToggleGroup navGroup;
    private DetailController detailController;
    private InterviewController interviewController;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        String url = resolveDbUrl();
        ensureDbParentDir(url);
        try {
            service = new ScoringService(url);
        } catch (RuntimeException e) {
            Dialogs.error("无法打开数据库：" + e.getMessage()
                    + "\n\n请确认：1) lib/ 目录含 H2 驱动 jar；2) data/ 目录可写；"
                    + "3) 没有其它程序（如 CLI）正在使用同一数据库。");
            return;
        }
        ctx = new AppContext(service, this);

        buildUi(primaryStage, url);
        // 窗口关闭前先关闭数据库连接（closeService 幂等；随后显式退出 JavaFX 平台，
        // 保证无论是否为主窗口关闭都能走到 stop()/closeService，锁正常释放）
        primaryStage.setOnCloseRequest(e -> {
            closeService();
            Platform.exit();
        });
        primaryStage.show();

        // 启动时检测未完成面试 → 弹「恢复上次未完成面试」
        checkResume();
    }

    @Override
    public void stop() {
        closeService();
    }

    private void closeService() {
        if (service != null) {
            try {
                service.close();
            } catch (RuntimeException ignored) {
                // 关闭失败不影响退出
            }
            service = null;
        }
    }

    // ------------------------------------------------------------------ UI 骨架

    private void buildUi(Stage primaryStage, String dbUrl) {
        root = new BorderPane();
        root.getStyleClass().add("app-root");

        root.setTop(buildTopBar(dbUrl));
        root.setLeft(buildSidebar());

        Scene scene = new Scene(root, 1100, 720);
        URL css = Main.class.getResource("app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        // 应用图标（app-icon.png 与 app.css 同目录随资源打包）
        URL iconUrl = Main.class.getResource("app-icon.png");
        if (iconUrl != null) {
            primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
        primaryStage.setScene(scene);
        primaryStage.setTitle("学生组织面试评分系统");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        show("dashboard");
    }

    private HBox buildTopBar(String dbUrl) {
        Label app = new Label("学生组织面试评分系统");
        app.getStyleClass().add("app-title");
        Label db = new Label("数据库：" + dbUrl);
        db.getStyleClass().add("db-path");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(16, app, spacer, db);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private VBox buildSidebar() {
        navGroup = new ToggleGroup();
        VBox side = new VBox(4);
        side.getStyleClass().add("sidebar");

        addNavButton(side, "dashboard", "仪表盘");
        addNavButton(side, "import", "名单导入");
        addNavButton(side, "candidates", "候选人");
        addNavButton(side, "interview", "面试打分");
        addNavButton(side, "ranking", "排名");
        addNavButton(side, "undo", "撤销");

        Separator sep = new Separator();
        sep.getStyleClass().add("sidebar-sep");
        side.getChildren().add(sep);

        Button reset = new Button("初始化系统");
        reset.getStyleClass().addAll("nav-btn", "nav-danger");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(e -> {
            boolean done = ResetFlow.run(ctx, stage);
            if (done) {
                // 初始化后所有视图数据失效，重新切入当前页以刷新
                show(currentPageId());
            }
        });
        side.getChildren().add(reset);

        return side;
    }

    private void addNavButton(VBox side, String id, String text) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setToggleGroup(navGroup);
        b.setOnAction(e -> show(id));
        navButtons.put(id, b);
        side.getChildren().add(b);
    }

    private String currentPageId() {
        for (Map.Entry<String, ToggleButton> e : navButtons.entrySet()) {
            if (e.getValue().isSelected()) {
                return e.getKey();
            }
        }
        return "dashboard";
    }

    // ------------------------------------------------------------------ Navigator 实现

    @Override
    public void show(String pageId) {
        Page page = pages.computeIfAbsent(pageId, this::createPage);
        if (page == null) {
            return;
        }
        ToggleButton b = navButtons.get(pageId);
        if (b != null) {
            b.setSelected(true);
        }
        root.setCenter(page.view());
        page.refresh();
    }

    @Override
    public void showDetail(String studentNo) {
        Page page = pages.computeIfAbsent("detail", this::createPage);
        detailController.setStudentNo(studentNo);
        root.setCenter(page.view());
        page.refresh();
    }

    @Override
    public void showInterview(String studentNo) {
        Page page = pages.computeIfAbsent("interview", this::createPage);
        interviewController.requestCandidate(studentNo);
        ToggleButton b = navButtons.get("interview");
        if (b != null) {
            b.setSelected(true);
        }
        root.setCenter(page.view());
        page.refresh();
    }

    private Page createPage(String id) {
        return switch (id) {
            case "dashboard" -> new DashboardController(ctx);
            case "import" -> new ImportController(ctx);
            case "candidates" -> new CandidatesController(ctx);
            case "interview" -> {
                interviewController = new InterviewController(ctx);
                yield interviewController;
            }
            case "ranking" -> new RankingController(ctx);
            case "undo" -> new UndoController(ctx);
            case "detail" -> {
                detailController = new DetailController(ctx);
                yield detailController;
            }
            default -> null;
        };
    }

    /** 启动时：有未完成面试 → 询问恢复。 */
    private void checkResume() {
        try {
            CandidateInfo cur = service.currentInterviewing();
            if (cur == null) {
                return;
            }
            boolean resume = Dialogs.confirm("恢复上次未完成面试",
                    "检测到上次未完成的面试：" + cur.name() + "（" + cur.studentNo() + "）。\n\n"
                            + "已录入的评分都还在。是否现在继续该候选人的面试？\n"
                            + "（选「取消」则稍后可在「面试打分」页继续）");
            if (resume) {
                showInterview(cur.studentNo());
            }
        } catch (RuntimeException e) {
            Dialogs.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 数据库定位

    /** 解析数据库 URL：prop → env → {@link #defaultDbUrl()}。 */
    public static String resolveDbUrl() {
        String prop = System.getProperty("scoring.db.url");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("SCORING_DB_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return defaultDbUrl();
    }

    /**
     * 默认数据库 URL（绝对路径）。候选目录按优先级：
     * ① 启动目录 {@code data/scoring}（开发/便携运行）；② 主程序（jar/class）所在目录
     * {@code data/scoring}（便携 app-image）；③ 用户主目录 {@code .scoring-gui/data/scoring}
     * （安装在 Program Files 等不可写目录时）。取第一个父目录可创建且可写者。
     */
    public static String defaultDbUrl() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(DEFAULT_DB_PATH).toAbsolutePath().normalize());
        try {
            Path self = Path.of(Main.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(self)) {
                candidates.add(self.resolve("data").resolve("scoring").toAbsolutePath().normalize());
            }
        } catch (Exception ignored) {
            // class 目录不可定位时跳过本候选
        }
        candidates.add(Path.of(System.getProperty("user.home"), ".scoring-gui", "data", "scoring")
                .toAbsolutePath().normalize());
        for (Path p : candidates) {
            try {
                Files.createDirectories(p.getParent());
                if (Files.isWritable(p.getParent())) {
                    return "jdbc:h2:file:" + p.toString().replace('\\', '/');
                }
            } catch (Exception ignored) {
                // 该候选不可用，尝试下一个
            }
        }
        return "jdbc:h2:file:" + candidates.get(candidates.size() - 1).toString().replace('\\', '/');
    }

    /** H2 不会自动创建父目录；确保 jdbc:h2:file:<路径> 的父目录存在。 */
    private static void ensureDbParentDir(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:h2:file:")) {
            return;
        }
        String p = jdbcUrl.substring("jdbc:h2:file:".length());
        int semi = p.indexOf(';');
        if (semi >= 0) {
            p = p.substring(0, semi);
        }
        File parent = new File(p).getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
    }
}
