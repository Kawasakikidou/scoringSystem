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

## 发布（Release）产物

`dist/` 保存随 GitHub Release 一同发布的产物（随仓库入库，更新版本时重新生成）：

| 文件 | 说明 | 重新生成 |
|---|---|---|
| `dist/ScoringGUI-win-package.zip` | **Windows 一键打包源包**：源码+脚本+文档+样例+H2 驱动（60 余项，约 3 MB） | `bash scripts/package-source.sh` |
| `dist/ScoringGUI-1.0.0.exe` | Windows 无黑框安装器（**在 Windows 打包机上**由下面命令生成后放入 `dist/` 提交/挂附件） | `scripts\package-win.bat` |

```
# 1) Linux/macOS：重新生成 Windows 源包（已含最新文档/源码）
bash scripts/package-source.sh

# 2) Windows 打包机：解压源包 → 装 JDK17 → （进阶）新增/更新版本后
scripts\package-win.bat      # 自动：检查环境→下载组件→编译→jpackage→dist\ScoringGUI-1.0.0.exe
```

- 打包必须在 Windows 机器上执行（需要**完整版** JDK 17，含 `jpackage`）；
- 完整说明见 `docs/GUI打包指南-win.md`；打包参数与原理见《接口文档》§7.4；
- 数据位置：默认「启动目录/data/」；装在 Program Files 等不可写目录时自动回退
  「用户主目录\.scoring-gui\data\」。

### 打 GitHub Release（计划动作，命令含 gh 或网页操作）

```bash
# 方式一：命令行（须安装 gh 并登录）
gh release create v1.0.0 --title "v1.0.0 — GUI 前端与 Win 安装器" \
  --notes "学生组织面试评分系统 GUI（JavaFX 17）+ Windows 安装器源包" \
  "dist/ScoringGUI-win-package.zip"

# 之后拿到 Windows 上产出的 ScoringGUI-1.0.0.exe 可再上传到同一 Release：
gh release upload v1.0.0 dist/ScoringGUI-1.0.0.exe
```

方式二（网页）：GitHub 仓库 → Releases → New release → 输入 Tag `v1.0.0` →
拖入 `dist/ScoringGUI-win-package.zip`（及 Windows 产出的 `ScoringGUI-1.0.0.exe`）→ Publish。
最终用户会看到顺序：**先按序号下载「源包/安装器」→ 安装/打包 → 读 `docs/GUI使用说明.md`**。

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
