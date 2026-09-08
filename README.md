# 学生组织面试评分系统（Java 17 后端 + CLI + GUI）——二期

面向学生组织纳新的面试评分工具：导入报名名单（txt/csv/**xlsx/xls**）→ 逐人**四维**面试打分
（责任心/时间管理能力/学生工作能力/部门契合度，各 0~25，逐维去极值）→ 排名 → 导出
Excel 可读 CSV（UTF-8 BOM）。另含名单管理（增/改/删/级联）、旧库自动迁移与“彻底重置”。
core 层纯业务、零 UI 依赖；CLI 与 JavaFX GUI 两个壳都只依赖 core（接口见 `docs/接口文档.md`）。

## 目录

```
lib/       第三方 jar：h2-2.2.224.jar + Apache POI 5.2.5 系（xlsx/xls 读取）、openjfx-17/<platform>/（GUI）
icons/     应用图标：app-icon.jpg（原图）、ScoringGUI.ico（Windows 打包/向导壳）、app-icon.png（JavaFX 窗口）
data/      运行期数据库 scoring.mv.db 与 CSV 导出（已 gitignore；彻底重置会删除）
src/core/  core 层：ScoringService + DTO + RosterParser/ExcelRosterReader/Store（可独立编译）
src/cli/   中文交互 CLI 壳（仅依赖 core）
src/gui/   JavaFX GUI 壳（仅依赖 core；二期改造指引见 docs/GUI变更步骤.md）
samples/   验收样例：规整/乱序 txt + 四维.xlsx/.xls（口径一致）
docs/      接口文档、CLI/GUI 使用说明、GUI 变更步骤、自测记录
scripts/   fetch-libs(-v2) / build / run / gen-excel-samples / self-test / acceptance（CLI 与 GUI 各一套）
```

## 快速开始

```bash
# 方式 A：一键验收（推荐先跑这个：环境检查→下载 H2+POI→生成 Excel 样例→编译→全流程断言）
# 前置：JDK 17；python3 + openpyxl/xlwt。Debian/Ubuntu（PEP 668）推荐装配项目 venv：
#   python3 -m venv .venv && .venv/bin/pip install openpyxl xlwt
#   （acceptance.sh/self-test.sh 会自动优先使用 .venv；备选：sudo apt install
#     python3-openpyxl python3-xlwt，或 pip install --break-system-packages openpyxl xlwt）
bash scripts/acceptance.sh
# 依赖下载需代理时：bash scripts/acceptance.sh -x http://127.0.0.1:7890

# 方式 B：分步执行
# 1) 首次：下载 H2 + Apache POI 到 lib/（需要联网；公司代理加 -x http://代理地址）
bash scripts/fetch-libs.sh

# 2) 生成 xlsx/xls 验收样例（需 python3 + openpyxl/xlwt；self-test 全流程需要）
bash scripts/gen-excel-samples.py

# 3) 编译（core 先独立编译自证无 UI 依赖，再全量编译 core+cli）
bash scripts/build.sh

# 4) 运行（中文交互菜单）
bash scripts/run.sh

# 可选：非交互式导入一次后退出
bash scripts/run.sh import samples/名单样例-规整.txt
```

Windows：`scripts\fetch-libs-v2.bat`（ASCII 全量版，H2+POI；打包链用的 GBK 版
`fetch-libs.bat` 不含 POI，二期 Excel 功能请用 v2 版）→ `scripts\build.bat` →
`chcp 65001` → `scripts\run.bat`。

## GUI（JavaFX 17 图形界面）

GUI 与 CLI 功能一一对应，全部业务仍只走 core 层；面向非技术面试官的操作手册见 `docs/GUI使用说明.md`。
> ⚠ 二期已将 core 升级为四维评分/名单 CRUD/彻底重置，当前 GUI 仍绑定一期 API，**需按
> `docs/GUI变更步骤.md` 改造后才可编译运行**（本阶段按计划不动 GUI 源码）。

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

`dist/` 保存随 GitHub Release 一同发布的产物（**不入库**，由打包脚本本地生成后作为 Release 附件上传）：

| 文件 | 说明 | 重新生成 |
|---|---|---|
| `dist/ScoringGUI-Setup-1.0.0.exe` | **推荐交付物**——安装向导壳（内嵌安装引擎与图标）：双击安装，完成页可勾选「删除安装包」/立即运行；程序已安装时再双击提供 启动/卸载/关闭 管理页（卸载走标准 Windows 向导） | `scripts\package-win.bat`（第 7 步自动组装，也可单独 `scripts\build-setup.bat`） |
| `dist/ScoringGUI-1.0.0.exe` | 纯安装引擎（jpackage 直出，供重打包等特殊场景；日常请发给同事 Setup 版） | `scripts\package-win.bat` |
| `dist/ScoringGUI-win-package.zip` | （可选）Windows 一键打包源包：源码+脚本+文档+样例+H2 驱动（约 3 MB） | `bash scripts/package-source.sh` |

```
# Windows 打包机：解压源包 → 安装完整版 JDK 17 → 双击
scripts\package-win.bat
# 自动完成：环境检查（jpackage 缺失时按注册表自动补 PATH）→ 补下 H2/JavaFX
# → 编译 core+gui → 组装 scoring-gui.jar → 补下 WiX（lib\wix3，仅首次）→
# jpackage 生成引擎（含应用图标）→ 组装 Setup 安装向导壳
# 产出：dist\ScoringGUI-1.0.0.exe + dist\ScoringGUI-Setup-1.0.0.exe
```

- 打包必须在 Windows 机器上执行（需要**完整版** JDK 17，含 `jpackage` 与 `javac`；无需预装 WiX、无需把 JDK bin 加进 PATH，脚本会自动定位；csc 用系统自带 .NET Framework 4.x）；
- ⚠ 编码约定：**打包链路 bat**（`package-win` / `build-gui` / `build-setup` / `fetch-libs` / `fetch-gui-libs`）
  以**系统 ANSI（GBK）编码**保存（与 cmd 默认代码页一致，双击可直接运行），请勿另存为 UTF-8；
  其余 `run*.bat`/`build.bat` 保持 UTF-8（内含首行 `chcp 65001` 自理）；`SetupWizard.cs` 为 UTF-8
  （编译时 `csc /codepage:65001`）；
- 完整说明见 `docs/GUI打包指南-win.md`；打包参数与原理见《接口文档》§7.4；
- 数据位置：默认「启动目录/data/」；装在 Program Files 等不可写目录时自动回退
  「用户主目录\.scoring-gui\data\」。

### 打 GitHub Release（命令含 gh 或网页操作）

```bash
# 方式一：命令行（须安装 gh 并登录）
gh release create v1.0.0 --title "v1.0.0 — GUI 前端与 Windows 安装器" \
  --notes "学生组织面试评分系统 GUI（JavaFX 17）+ Windows 安装器（Setup 向导壳）" \
  "dist/ScoringGUI-Setup-1.0.0.exe"

# 如需一并提供源包/引擎：
gh release upload v1.0.0 dist/ScoringGUI-win-package.zip dist/ScoringGUI-1.0.0.exe
```

方式二（网页）：GitHub 仓库 → Releases → New release → 输入 Tag → 拖入
`dist/ScoringGUI-Setup-1.0.0.exe`（为主附件；可选附源包/引擎）→ Publish。
最终用户会看到顺序：**下载 Setup 安装器 → 双击安装 → 读 `docs/GUI使用说明.md`**。
卸载请走：Windows 设置 → 应用 → ScoringGUI → 卸载（或再双击 Setup 走管理页【卸载】）。

也可以手工编译（验收方式，JDK 17；core 使用 POI，编译期需要 lib/* classpath）：

```bash
# core 独立编译（classpath=lib/*；不含任何 GUI/CLI 依赖）
javac -encoding UTF-8 -cp 'lib/*' -d out/core-only $(find src/core -name '*.java')
# 全量编译（core+cli）
javac -encoding UTF-8 -cp 'lib/*' -d out $(find src/core -name '*.java') $(find src/cli -name '*.java')
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
| `samples/名单样例-乱序.txt` | 成功 10 行（新增 9、更新 1）/ 跳过 3（11 位手机号行、18 位连写数字行、无学号行） |
| `samples/名单样例-四维.xlsx`（`scripts/gen-excel-samples.py` 生成） | 与乱序 txt 口径一致（10/9/1/3） |
| `samples/名单样例-四维.xls`（同上） | 与乱序 txt 口径一致（10/9/1/3） |

一键验收：`bash scripts/acceptance.sh`（依赖→编译→生成样例→自测断言全流程）。
二期验收记录见 `docs/自测记录.md`；GUI 四维改造指引见 `docs/GUI变更步骤.md`。

## 许可证

[![License: CC BY 3.0](https://licensebuttons.net/l/by/3.0/80x15.png)](https://creativecommons.org/licenses/by/3.0/)

本仓库（代码、文档、脚本、图标与样例）以 **知识共享署名 3.0 未本地化**
（[Creative Commons Attribution 3.0 Unported](https://creativecommons.org/licenses/by/3.0/)）
发布，全文见根目录 [`LICENSE`](LICENSE)。

- **署名要求**：以任何方式使用/修改/再分发时，须注明原作者与来源链接，例如：
  © 2026 Kawasakikidou（[scoringSystem](https://github.com/Kawasakikidou/scoringSystem)，
  liweiheng@mails.gdut.edu.cn），并说明是否对作品做了修改（CC BY 3.0 条款）；
- 可自由使用/修改/商用，条件即上述"署名 + 注明修改"；
- 样例名单为虚构数据，仅作验收用途。
