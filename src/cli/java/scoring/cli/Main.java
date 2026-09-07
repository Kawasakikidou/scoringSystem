package scoring.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * CLI 程序入口（仅依赖 core 层；本身不含任何业务逻辑）。
 *
 * <p>用法：
 * <ul>
 *   <li>直接运行：进入中文交互菜单；</li>
 *   <li>{@code Main import [名单文件]}：非交互式导入一次后退出（供脚本/验收使用），
 *       缺省名单文件为当前目录下「名单.txt」。</li>
 * </ul>
 *
 * <p>数据库位置解析顺序：系统属性 {@code scoring.db.url} →
 * 环境变量 {@code SCORING_DB_URL} → 默认 {@code jdbc:h2:file:data/scoring}（相对当前工作目录）。
 */
public final class Main {

    public static final String DEFAULT_ROSTER = "名单.txt";
    public static final String DEFAULT_DB_URL = "jdbc:h2:file:data/scoring";

    private Main() {
    }

    public static void main(String[] args) {
        setUpUtf8Stdio();
        String url = resolveDbUrl();
        ensureDbParentDir(url);

        if (args.length > 0 && !"import".equalsIgnoreCase(args[0])) {
            printUsage(System.out);
            return;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        if (args.length > 0) {
            // 非交互模式：Main import [名单文件路径]
            String roster = args.length > 1 ? args[1] : DEFAULT_ROSTER;
            boolean ok = new CliApp(in, System.out, url).importOnly(roster);
            if (!ok) {
                System.exit(1);
            }
            return;
        }
        new CliApp(in, System.out, url).runLoop();
    }

    /** Windows cmd 可能是 GBK 控制台：以 UTF-8 字节流重包 stdout/stderr；输入侧同见 CliApp。 */
    private static void setUpUtf8Stdio() {
        // FileOutputStream(FileDescriptor) 与 PrintStream(…, Charset) 均不抛受检异常
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    static String resolveDbUrl() {
        String prop = System.getProperty("scoring.db.url");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("SCORING_DB_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_DB_URL;
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

    private static void printUsage(PrintStream out) {
        out.println("用法：");
        out.println("  1) java -cp out:lib/* scoring.cli.Main           进入中文交互菜单");
        out.println("  2) java -cp out:lib/* scoring.cli.Main import [名单文件]  导入一次后退出");
        out.println("  3) java -cp out:lib/* scoring.cli.Main --help    显示本帮助");
        out.println("环境变量：SCORING_DB_URL 可覆盖数据库位置（默认 " + DEFAULT_DB_URL + "）");
    }
}
