# GUI 打包指南（Windows）—— 一键生成无黑框 exe

> 适用：把「学生组织面试评分系统 GUI」打包成可供同事直接双击安装的 **Windows exe 安装器**。
> 打包动作必须在 **Windows 机器**上执行（jpackage 生成 Windows 安装器不支持在 Linux 上交叉完成）；
> 本文面向会装 JDK 的发布者（非一般最终用户；最终用户手册为 `GUI使用说明.md`）。

---

## 1. 需要准备什么

| 项 | 说明 |
|---|---|
| 一台 Windows 10/11 电脑 | 打包机即可（不用是最终用机），64 位 |
| 完整版 **JDK 17** | 必须含 `jpackage` 与 `javac` 工具（`java -version` 显示 17.0.x；`where jpackage` 有输出）。JRE 不行 |
| 本仓库源文件 | 源码 + 脚本 + 文档都齐全；无法访问的电脑用 `dist/ScoringGUI-win-package.zip`（一键包内已含全部） |
| 联网一次 | 首次打包会下载 H2 驱动与 JavaFX 17 SDK（之后缓存在 `lib/`，不再下载） |

> 「一键包」`dist/ScoringGUI-win-package.zip`：解压后即为完整项目骨架
> （src/core、src/gui、scripts、docs、samples、lib/h2），在 Windows 上解压 → 步骤 2 → 完成。

## 2. 一键打包（三步）

1. 解压一键包（或已有项目），进入项目根目录；
2. 双击运行 `scripts\package-win.bat`（或在 cmd 中执行）；
   - 脚本会：检查 JDK17/jpackage → 自动补下 H2 与 JavaFX SDK → javac 编译 core+gui → 组装
     `scoring-gui.jar`（含 CSS 资源，主类 `scoring.gui.Main`）→ 调用 `jpackage`；
3. 结束后在 `dist\` 下得到 **`ScoringGUI-1.0.0.exe`** —— 这就是成品安装器。

可选控制台操作（Windows 终端乱码时先执行 `chcp 65001`）。

## 3. 安装器使用的关键参数（与 package-win.bat 完全一致）

```
jpackage --type exe --name ScoringGUI --app-version 1.0.0 --vendor "ScoringGUI" ^
  --input dist-pkg --main-jar scoring-gui.jar --main-class scoring.gui.Main ^
  --module-path lib\openjfx-17\windows-jmods ^
  --add-modules javafx.controls,javafx.fxml,java.sql,java.logging,java.management,java.naming ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --dest dist --win-console=false --win-shortcut --win-per-user-install
```

- `--win-console=false`：**关键**——GUI 子系统，运行时不出现命令行黑框；
- `--module-path …-jmods --add-modules …`：把 JavaFX **jmods** 链入内置运行时
  （应用仍在 classpath），运行机**无需另装 Java**。⚠ 为什么用 jmods 而非 SDK 的 `windows\lib`：
  SDK 里的 `*.dll` 是松散文件，jlink/jpackage 不会把它们带入运行时——那样产出的安装包一启动
  就报 `no suitable pipeline found`。jmods 包内嵌了原生库，是打包的正确输入；两者都由
  `fetch-gui-libs.bat` 一次性下载（各约 60~90 MB，仅首次）。
- `--add-modules` 里的 `java.sql/java.logging/java.management/java.naming`：classpath 应用没有
  主模块，jlink 只收集本清单模块；不写 `java.sql` 会报 `NoClassDefFoundError: java/sql/SQLException`；
- `--win-per-user-install`：按用户安装，无需管理员权限；需要全机器安装可去掉本参数；
- `--win-shortcut`：安装时自动创建开始菜单/桌面快捷方式。

## 4. 安装与运行验证（发布前必做的冒烟）

1. 双击 `ScoringGUI-1.0.0.exe` → 按向导安装完成（开始菜单出现「ScoringGUI」）；
2. 从开始菜单/桌面快捷方式启动 → 出现「学生组织面试评分系统」窗口（1100×720，无黑框）；
3. 快速冒烟（详见 `GUI使用说明.md` / `GUI交付说明.md` 冒烟清单）：
   导入 `samples\名单样例-规整.txt` → 搜索 → 开始面试 → 打几条分 → 结束 → 排名页导出 CSV →
   用 Excel 双击打开（UTF-8 BOM 不乱码）。

## 5. 数据与目录说明（打包后与开发版一致）

| 项 | 位置 |
|---|---|
| 默认数据库 | 启动目录 `data\scoring.mv.db`；若安装在不可写目录（如 `C:\Program Files\`），
  程序自动改用 `用户主目录\.scoring-gui\data\`（不会因权限失败） |
| 数据备份 | 关闭程序后复制 `scoring.mv.db` 一个文件；或用系统内「导出 CSV（全部）」留档 |
| 更换数据库位置 | 设置环境变量 `SCORING_DB_URL=jdbc:h2:file:绝对路径/scoring` 再启动 |

## 6. 常见问题

| 问题 | 处理 |
|---|---|
| `不需要的 jpackage`（where jpackage 无输出） | 安装「完整版」JDK 17（Oracle/Adoptium/Azul 等发行版均可），而非 JRE |
| 打包报错「安装包扩展名/文件冲突」 | 先删除 `dist\` 下旧安装包，确认无同名 `ScoringGUI-1.0.0.exe` 被占用 |
| 打包极慢/下载卡住 | `fetch-gui-libs.bat` 首次下载约 150 MB（SDK 与 jmods 两份）；可先单独运行一次确认网络（有代理时 `-x http://代理` 后手动重跑） |
| 产出的 exe 装了却打不开/白屏 | 多为 module-path 用错目录（需 `windows-jmods` 而非 `windows\lib`）或 `--add-modules` 缺 `java.sql`；对照第 3 节检查 |
| 安装后启动提示数据库打不开 | 多为目录权限：程序会自动回退到「用户主目录\.scoring-gui\data\」；也可设环境变量指定位置 |
| 目标电脑没有 Java | 无需安装——jpackage 已把运行时打进安装包 |
