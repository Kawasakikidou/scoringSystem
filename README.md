# 学生组织面试评分系统（Java 17 后端 + CLI + GUI）

面向学生组织纳新的面试评分工具：导入报名名单（容忍乱序多列文本）→ 逐人面试打分 →
自动去极值算最终分 → 排名 → 导出 Excel 可读 CSV（UTF-8 BOM）。
core 层纯业务、零 UI 依赖；CLI 与 JavaFX GUI 两个壳都只依赖 core（接口见 `docs/接口文档.md`）。

## 目录

```
lib/       第三方 jar：h2-2.2.224.jar（数据库）、openjfx-17/<platform>/（JavaFX 17 SDK，GUI 用）
data/      运行期数据库 scoring.mv.db 与 CSV 导出（已 gitignore）
src/core/  core 层：ScoringService + DTO + 内部解析器/DAO（可无依赖独立编译）
src/cli/   中文交互 CLI 壳（仅依赖 core）
src/gui/   JavaFX GUI 壳（仅依赖 core；java/ 源码 + resources/ 界面样式）
samples/   验收样例：规整名单 / 乱序多列含坏行名单
docs/      接口文档、CLI/GUI 使用说明、自测记录
scripts/   fetch-libs / build / run（CLI 与 GUI 各一套，sh 与 bat）
```

## 快速开始

```bash
# 方式 A：一键完成 依赖下载 → 编译 → 端到端验收（推荐先跑这个）
bash scripts/acceptance.sh

# 方式 B：分步执行
# 1) 首次：下载 H2 驱动到 lib/（需要联网）
bash scripts/fetch-libs.sh

# 2) 编译（core 先独立编译自证无 UI 依赖，再全量编译 core+cli）
bash scripts/build.sh

# 3) 运行（中文交互菜单）
bash scripts/run.sh

# 可选：非交互式导入一次后退出
bash scripts/run.sh import samples/名单样例-规整.txt
```

Windows：`scripts\fetch-libs.bat` → `scripts\build.bat` → `chcp 65001` → `scripts\run.bat`。

## GUI（JavaFX 17 图形界面）

GUI 与 CLI 功能一一对应，全部业务仍只走 core 层；面向非技术面试官的操作手册见 `docs/GUI使用说明.md`。

```bash
# 1) 首次：下载 H2 与 JavaFX 17 SDK（Gluon 官方；脚本自动识别平台，-x 可透传代理）
bash scripts/fetch-libs.sh
bash scripts/fetch-gui-libs.sh            # 如需代理：bash scripts/fetch-gui-libs.sh -x http://127.0.0.1:7890

# 2) 编译 core + gui（javac，classpath 含 openjfx 与 h2）
bash scripts/build-gui.sh

# 3) 启动 GUI（1100×720 中文窗口）
bash scripts/run-gui.sh
```

Windows：`scripts\fetch-gui-libs.bat` → `scripts\build-gui.bat` → `scripts\run-gui.bat`
（需 JDK 17；控制台中文先 `chcp 65001`，bat 已自动设置）。
跨平台分发：Linux 机器上可用 `bash scripts/fetch-gui-libs.sh windows` 额外下载 Windows 版 SDK。

> ⚠ **GUI 与 CLI 共用同一数据库（`data/scoring.mv.db`），单进程单实例**：
> 数据互通，但同一时刻只能运行其中一个程序，先退出一个再开另一个。

## 打包为 Windows exe（无黑框安装器）

打包必须在 Windows 机器上执行（需要完整版 JDK 17，含 `jpackage`）：

```
scripts\package-win.bat      # 一键：检查环境 → 自动下载组件 → 编译 → jpackage → dist\ScoringGUI-1.0.0.exe
```

- 也可双击运行；`dist\ScoringGUI-win-package.zip`（Linux/macOS 上由
  `bash scripts/package-source.sh` 或手工生成）为「Windows 拿到即可打包」的一键源包；
- 完整说明见 `docs/GUI打包指南-win.md`；打包参数与原理见《接口文档》§7.4。
- 数据位置：默认「启动目录/data/」；装在 Program Files 等不可写目录时自动回退
  「用户主目录\.scoring-gui\data\」。

也可以手工编译（验收方式，JDK 17）：

```bash
# core 独立编译（证明无 UI/控制台依赖）
javac -encoding UTF-8 -d out/core-only $(find src/core -name '*.java')
# 全量编译（cli 依赖 core；H2 仅运行期需要）
javac -encoding UTF-8 -d out $(find src/core -name '*.java') $(find src/cli -name '*.java')
# 运行
java -Dfile.encoding=UTF-8 -cp out:lib/* scoring.cli.Main
```

GUI 手工编译/运行（以 linux 平台为例，windows 换成 `lib/openjfx-17/windows/lib` 与 `;` 分隔）：

```bash
javac -encoding UTF-8 -d out -cp "out:lib/h2-2.2.224.jar:lib/openjfx-17/linux/lib/*" \
    $(find src/core -name '*.java') $(find src/gui -name '*.java')
cp -r src/gui/resources/. out/
java -Dfile.encoding=UTF-8 --module-path lib/openjfx-17/linux/lib \
    --add-modules javafx.controls,javafx.fxml \
    -cp out:lib/h2-2.2.224.jar scoring.gui.Main
```

## 数据与配置

- 数据库：`data/scoring.mv.db`（H2 嵌入式文件库），可用环境变量 `SCORING_DB_URL` 或
  `-Dscoring.db.url` 覆盖（默认 `jdbc:h2:file:data/scoring`，相对启动目录）。
- 默认名单文件：当前目录 `名单.txt`（菜单里可输入任意路径）。
- 全量规则/语义/限制请读 `docs/接口文档.md`；面向非技术使用者的操作手册为
  `docs/CLI使用说明.md` 与 `docs/GUI使用说明.md`；验收流程与输出见 `docs/自测记录.md`。

## 验收样例

| 文件 | 预期（空库首次导入） |
|---|---|
| `samples/名单样例-规整.txt` | 成功 8 行（新增 8）/ 跳过 0 |
| `samples/名单样例-乱序.txt` | 成功 10 行（新增 9、更新 1）/ 跳过 3（手机号行、18 位连写学号行、无学号行） |
