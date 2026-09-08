# GUI 版交付说明

> 交付范围：`src/gui/`（JavaFX 17 GUI 壳）、`scripts/fetch-gui-libs|build-gui|run-gui`（sh+bat）、
> `docs/GUI使用说明.md`、README 与《接口文档》§7.4 增补。既有 core/cli 未做任何改动
> （`scripts/build.sh` 与 `scripts/acceptance.sh` 复验通过，见下文）。

## 1. 分层与依赖自检

- GUI 全部 `import scoring.*` 仅两类：`scoring.core.ScoringService` 与 `scoring.core.dto.*`
  （其余为 `scoring.gui.*` 自身、`java.*`、`javafx.*`），**无 `scoring.cli.*`**；
- **无 `System.out/System.in/System.err` 实际代码引用**（仅 Main.java 一行 Javadoc 提及该约束本身）；
- **无任何 SQL 语句**（Main.java 中只有 JDBC URL 常量 `jdbc:h2:file:data/scoring` —— 这是
  《接口文档》§5.2 约定的 ScoringService 构造参数，与 CLI 的 Main 同款配置项，不是 SQL）；
- 业务规则零复刻：评分校验只调 core 的 `ScoringService.MIN_VALUE/MAX_VALUE` 常量做即时输入校验，
  最终裁决（解析/计分/撤销/状态机）全部在 core。

页面 ↔ core 方法映射：

| 页面 | ScoringService 方法 |
|---|---|
| 仪表盘 | `stats()`、`currentInterviewing()` |
| 名单导入 | `importRoster(Path)` |
| 候选人列表 | `listCandidates()`、`searchCandidates(kw)`（空关键字回退 list） |
| 面试打分 | `beginInterview`、`candidateDetail`、`addInterviewScore`、`deleteLastInterviewScore`、`finishInterview(no,false/true)` |
| 候选人明细 | `candidateDetail`、`addMakeupScore`、`addBonus` |
| 撤销 | `latestOperation()`、`undoLatest()` |
| 排名/导出 | `ranking()`、`listCandidates()`、`exportCsv(Path,includeUnfinished)` |
| 初始化向导 | `stats()`、`exportCsv`（备份）、`resetAll()` |
| 全局 | 窗口关闭前 `close()`；页面每次切入重拉数据，写操作就地刷新（最终分动态计算） |

## 2. 本机验收输出（Debian 12，JDK 17.0.20.1，无显示器 → 编译级验证）

### 2.1 依赖下载（验收标准 1）

```
$ bash scripts/fetch-gui-libs.sh -x http://127.0.0.1:7890
==> 验证下载地址（HEAD）：https://download2.gluonhq.com/openjfx/17.0.20/openjfx-17.0.20_linux-x64_bin-sdk.zip
==> 下载中……（59.2 MB）
==> 解压到 .../lib/openjfx-17/linux ...
完成：.../lib/openjfx-17/linux/lib （module-path 指向该目录）
```

版本定稿说明：按任务书「17.0.x 具体小版本以可下载为准」，经 HEAD 探测
17.0.13~17.0.20 均可下载、17.0.21 已 403，故取 **17.0.20**（linux/windows 双平台 URL 均 200）。
脚本不写死代理，`-x` 可选透传（Windows 无代理可直接跑 .bat）。

### 2.2 全量编译（验收标准 2）——零错误零警告

```
$ bash scripts/build-gui.sh
==> 编译 core + gui（平台 linux，JavaFX SDK 17）
==> 复制 GUI 资源（CSS）到 out/
编译通过。运行方式：
  bash scripts/run-gui.sh
```

（早期曾有一处 `Region` 漏导入与 7 处 JavaFX 表格列 varargs 的 unchecked 提示，
已修复/规范抑制；当前 `javac -Xlint:unchecked` 复核无任何输出。）

### 2.3 源码自检（验收标准 3）

```
1) System.out/in/err：仅 Main.java 第 41 行 Javadoc 注释提及（非代码） → 通过
2) import scoring.cli.*：无 → 通过
3) SQL 语句（select/insert/update/delete/create/drop）：无 → 通过
```

### 2.4 类加载级运行验证（本机无 DISPLAY 的极限验证）

```
$ java -Dfile.encoding=UTF-8 --module-path lib/openjfx-17/linux/lib \
    --add-modules javafx.controls,javafx.fxml -cp out:lib/h2-2.2.224.jar scoring.gui.Main
Caused by: java.lang.UnsupportedOperationException: Unable to open DISPLAY
```

主类被正确定位、JavaFX 模块与本地库均成功加载，仅因无显示器停在 DISPLAY ——
证明 module-path / classpath 接线正确，Windows 有桌面环境即可进入窗口。

### 2.5 既有后端回归

```
$ bash scripts/acceptance.sh   # 既有 CLI 端到端验收
验收结果：通过（22 条断言全部通过，退出码 0）
```

### 2.6 文档核对（验收标准 4）

`docs/GUI使用说明.md` 十章与《接口文档》§9.1 十节提纲一一对应
（安装启动/导入/列表搜索/打分/补录附加/撤销/排名导出/初始化/数据文件与 CLI 共用/FAQ），
截图位以「📷 截图占位」标注。源码与文档冲突：**无**；两处口径注记——
① §9.1 提到「首次数据目录选择」，GUI 与 CLI 一致固定默认 `data/`（环境变量可覆盖），不做首次选择；
② §7.4 已按交付事实改写为完整 jpackage 命令示例。

## 2.7 Linux（Debian 12，Xvfb 虚拟屏）实机运行验证

> 本机无物理显示器：用 **Xvfb 21**（免 root：`apt-get download xvfb` 后 `dpkg -x` 于用户目录运行，
> 无系统级安装）+ `xdotool` 驱动 + 逐屏截图人工读图核对 + H2（AUTO_SERVER 双连接）数据库断言。
> 界面代码/按钮坐标全部实测；以下为跑通的完整 UI 流程（与第 3 节 Windows 冒烟清单一一对应）：

| 步骤 | 结果 |
|---|---|
| 启动 → 仪表盘 6 张统计卡（名单 0/未面试 0/面试中 0/已结束 0/评分 0/附加 0） | ✅ 截图核对 |
| 名单导入 → FileChooser（GTK 原生对话框，Ctrl+L 输路径）→ 导入结果弹窗「成功 8 行/新增 8/跳过 0/UTF-8」 | ✅ 弹窗 + 库 8 行 PENDING |
| 候选人页：搜索 `2023000101` 精确 1 行 | ✅ 表格 1 行；空关键字「显示全部」= 8 行、黄色未面试徽标 |
| 面试打分：选人视图 → 开始 2023000108 → 逐条存 85.5 / 92 / 78.5（每条实时入表并显示绿色成功提示） | ✅ 库 3 条 + 界面序号 1/2/3 |
| 删除上一条（确认弹窗）→ 剩 2 条，op_log 记 DEL_SCORE | ✅ |
| 结束评分（2 条）→「评分不足 3 条 — 二次确认（普通平均 88.75）」→ 先取消（仍 INTERVIEWING）再确认 → 结算弹窗 88.75 | ✅ 库 FINISHED + finished_at |
| 面试 2023000101：80/90/85 → ≥3 条结束**直接结算**（去极值平均 85.00，无二次确认） | ✅ |
| 候选人列表双击行 → 明细页：评分表/附加分表/汇总卡 + 补录表单（PENDING 被置灰并提示「不可补录」） | ✅ |
| 明细页补录 95 → 汇总实时重算 87.50（去极值） | ✅ 库 4 条 |
| 附加分 5.5 + 原因 caiyi（必填）→ 最终分 93.00 | ✅ 库 bonus 1 条 |
| 撤销页：预览「添加附加分 5.50（caiyi）」→ 确认 → 结果卡「已撤销…剩余 4 条，最新最终分 87.50」 | ✅ 库 bonus=0、日志回退 |
| 排名页：名次 1（0108, 88.75, 普通平均）/名次 2（0101, 87.50），未完成区 6 人 | ✅ 库一致 |
| 导出 CSV：范围「全部（含未完成）」→ 保存 → 提示 8 行 / 文件实测 **UTF-8 BOM**（EF BB BF）、未完成者分数列留空 | ✅ 文件解析核对 |
| 初始化：风险提示 → 备份询问（选跳过备份）→ 输入框**手输 YES 才放行**（非 YES 时确认按钮禁用）→ 初始化完成 | ✅ 库：评分/附加/日志全 0、8 人全 PENDING |
| 初始化后「撤销」页：按钮禁用 + 「当前没有任何可撤销的评分操作」 | ✅ 截图核对 |
| 窗口关闭：程序化触发 WINDOW_CLOSE_REQUEST → closeService() → 进程 exit=0、H2 锁文件释放 | ✅ 专项事件测试 |

过程中发现并修复的 2 个真实问题：

1. **H2 2.2.224 拒绝相对路径 URL**：`jdbc:h2:file:data/scoring` 启动即报错
   （A file path that is implicitly relative to the current working directory is not allowed…）。
   这是库本身限制（CLI 默认值同样存在，其验收脚本用绝对路径 `SCORING_DB_URL` 绕过）。
   **GUI 已修复**：默认分支在 `Main.resolveDbUrl()` 内转为启动目录绝对路径（最终仍落 `data/`，
   显式 prop/env URL 原样透传不改写）；本验证即靠 `-Dscoring.db.url=…` 直跑通过。
2. **默认「最后窗口关闭自动退出」在无 WM 环境下不可靠**：`start()` 中补
   `setOnCloseRequest(e -> { closeService(); Platform.exit(); })`（幂等），
   专项事件测试确认进程干净退出且 H2 锁释放。

环境性说明（非代码问题）：本容器无中文字体，截图中汉字显示为方块（Windows 正常）；
GTK 文件对话框产生 dconf/Gtk-CRITICAL 告警为无 dbus/无 WM 的无头环境杂讯，真实桌面上不出现。

## 2.8 Windows exe 打包链路（Linux 侧等价预演）

> exe 安装器只能在 Windows 上由 jpackage 产出，本机做了**除「安装器封装」外的完整等价预演**：
> 用与 `package-win.bat` 完全相同的参数链在 Linux 上生成 app-image 并实机运行。

1. **jar 组装**：`jar --create --file dist-pkg/scoring-gui.jar --main-class scoring.gui.Main -C out .` → 内含
   `Main.class` 与 `app.css`，H2 驱动单独入输入目录（不塞 jar）；
2. **jpackage（初版）**：module-path 用 SDK `lib`（松散 `.so`/`.dll`）→ **产物启动报
   `no suitable pipeline found`**（jlink 不收集松散原生库）——实测确认了「SDK 目录不能直接用于
   打包」这一坑，已改用 **jmods 包**（`_bin-jmods.zip`）；
3. **jpackage（修正版）**：module-path=`openjfx-jmods`、`--add-modules javafx.controls,javafx.fxml,
   java.sql,java.logging,java.management,java.naming`（初版缺 `java.sql` 曾报
   `NoClassDefFoundError: java/sql/SQLException`，classpath 应用必须显式列出 JDBC 等基础模块）；
4. **产物运行验证**（Xvfb 实机）：
   - 在可写目录（`dist/ScoringGUI/`）启动 → 主窗口「学生组织面试评分系统」出现，`data/scoring.mv.db`
     就地生成，日志干净；
   - 在只读目录（`chmod 555`）启动 → 自动回退「用户主目录 `~/.scoring-gui/data/`」成功建库，
     只读目录未被误写。预演通过。
5. **一键脚本**：`scripts/package-win.bat`（Windows 一键 exe）、`scripts/package-source.sh`
   （Linux/macOS 生成 Windows 源包 `dist/ScoringGUI-win-package.zip`，现有 61 项/2.9MB 已验证）。

## 3. Windows 手工冒烟步骤清单（验收标准 5）

> 前置：Windows 装好 JDK 17 → `scripts\fetch-libs.bat` → `scripts\fetch-gui-libs.bat`
> → `scripts\build-gui.bat` → `scripts\run-gui.bat`。每步预期如下，逐条打勾。

| # | 操作 | 预期结果 |
|---|---|---|
| 1 | 运行 `scripts\run-gui.bat` | 打开 1100×720 中文窗口，左侧导航：仪表盘/名单导入/候选人/面试打分/排名/撤销/初始化系统 |
| 2 | 「名单导入」→ 选择 `samples\名单样例-规整.txt` | 弹窗：成功解析 8 行（新增 8 人、更新 0），跳过 0 行，编码识别 UTF-8 |
| 3 | 「候选人」→ 搜索框留空回车 | 列表显示 8 人，状态徽标均为黄色「未面试」 |
| 4 | 搜索框输入姓的前一个字（如「张」）回车 | 只显示匹配的人；清空再回车恢复全部 |
| 5 | 「面试打分」→ 选中第 1 人 →「开始面试」 | 进入打分界面，提示已录 0 条 |
| 6 | 输入 `85` →「保存评分」；再存 `92`、`78.5` | 每次保存后表格即时多一行，底部绿色 ✓ 提示 |
| 7 | 输入 `120` → 保存 | 弹「操作未完成：分值需在 0~100 之间」（GUI 即时校验拦截） |
| 8 | 输入 `88.555` → 保存 | 弹「最多支持两位小数」 |
| 9 | 「删除上一条（误输入）」→ 确认 | 表格少一行，剩余 3 条评分 |
| 10 | 再存 1 条（如 `90`）→「结束评分并计算最终分」 | ≥3 条：直接弹结算（去极值平均/附加合计/最终分） |
| 11 | 回到「面试打分」对第 2 人开始面试，只打 2 条（如 `70`、`90`）→「结束评分」 | 弹「评分不足 3 条 — 二次确认」，文案含「将按普通平均」；点「取消」→ 仍留打分界面；再点结束并「确定」→ 按普通平均结算 |
| 12 | 面试中直接关窗口 → 重新运行 run-gui.bat | 启动弹「恢复上次未完成面试」（若第 3 人面试中途退出时）——可配合第 5~11 步任选一人中途关闭验证 |
| 13 | 「候选人」双击已结束者 → 明细页「补录」输入 `95` → 提交 | 绿色 ✓ 提示，汇总卡最终分立即重算变大 |
| 14 | 明细页「附加分」：分值 `5`、原因留空 → 提交 | 弹「请填写附加分原因」 |
| 15 | 填原因「才艺加分」→ 提交 | ✓ 附加成功，附加分表多一行，最终分更新 |
| 16 | 「撤销」页 | 显示最近操作预览（给 X 添加附加分 5.00（才艺加分））→「撤销该操作」→ 确认 → 显示最新最终分；再撤销一次可撤回补录 |
| 17 | 「排名」页 | 排名表按最终分降序；下方「未完成面试」区列出其余人 |
| 18 | 「导出 CSV…」→ 选「全部（含未完成）」→ 保存 | 提示导出行数；用 Excel 双击打开不乱码（UTF-8 BOM） |
| 19 | 「初始化系统」 | ①风险提示（含评分/附加条数）→ ②可选导出备份 → ③输入框不输 YES 时「确认初始化」不可点；输入 `YES` 后执行 → 提示完成 |
| 20 | 初始化后看「仪表盘」 | 总人数不变（名单保留）、全部回到「未面试」、评分/附加条数归 0；「撤销」页按钮置灰提示无可撤销操作 |

---

## 4. 二期改造记录（四维评分 / 名单管理 / Excel 导入 / 彻底重置）

> 依据 `docs/GUI变更步骤.md`（二期唯一执行清单）逐节落实；只改动 `src/gui/**`、GUI 脚本与 GUI 文档，
> `git diff -- src/core src/cli` 为空（未越界）。

### 4.1 改动文件清单（git status --short）

见本次提交随附输出；概要：

- `src/gui/java/scoring/gui/controller/`：InterviewController（§2 四维表单/新签名/结算弹窗）、
  DetailController（§3 四维明细+汇总卡、补录四输入、附加≤10、§4 名单管理组接入、新增 ScrollPane 防裁切）、
  CandidatesController（§4 名单管理组接入）、RankingController（§5 四维 12 列+排序说明）、
  DashboardController（§7 统计文案）、ImportController（§6 xlsx/xls 过滤与提示）、ResetFlow（§8 新增 runEverything）、
  **NameListCard（新增，名单管理可复用操作组）**
- `src/gui/java/scoring/gui/ui/`：ScoreInputValidator（维度 0~25 / 附加 0~10，引用 core 常量）、Dialogs（confirmTyped 泛化）
- `src/gui/java/scoring/gui/Main.java`：migrationNotice() 迁移提示、侧栏红色「彻底重置」入口
- `src/gui/resources/scoring/gui/app.css`：新增 `.danger-button` 红色危险样式
- GUI 脚本：build-gui.sh/.bat、run-gui.sh/.bat（classpath 改 `lib/*` 通配，见 4.2 冲突记录）、
  package-source.sh（源包纳入 POI 系 jar）
- 文档：GUI使用说明.md（十节增补）、本文件、README GUI 章节

### 4.2 与文档/变更步骤的冲突记录（以源码为准）

1. **《GUI变更步骤》§1 称「build-gui/run-gui 无需改动」——与实际不符**：二期 core 新增 POI 依赖
   （ExcelRosterReader），而两个脚本 classpath 写死 `lib/h2-2.2.224.jar`，编译即报
   「程序包 org.apache.poi.ss.usermodel 不存在」。已把 build-gui.sh/.bat 编译 classpath 与
   run-gui.sh/.bat 运行 classpath 均改为 `lib/*` 通配（POI 系列随 fetch-libs 入 lib/）。
2. package-source.sh 原只打包 `lib/h2-2.2.224.jar`，Windows 源包在二期会缺 POI 编译失败——
   已改为打包 `lib/*.jar` 全部三方 jar（openjfx 大目录仍由 fetch-gui-libs 下载）。
3. core 未发现 bug，无需记录 core 侧问题。

### 4.3 GUI 变更步骤·附录自检清单（逐项自查）

- [x] `grep -r "\.value()\|\.average()" src/gui` 无残留（全部改为 rAvg/tAvg/sAvg/fAvg/dimensionTotal 等新字段）
- [x] 打分/补录表单 = 四输入（0~25 即时校验，回车按序流转，末框回车提交）
- [x] 附加分提示 0~10（`ScoringService.BONUS_MAX`）；原因必填（空即拦截）
- [x] 排名/明细/导出按二期列展示（排名 12 列；明细四维列；CSV 列由 core 输出，GUI 不拼列）
- [x] 名单管理增/改/删带确认与级联提示（删除/改号确认框列明级联影响；面试中置灰 + core 兜底）
- [x] 迁移提示（migrationNotice）与恢复面试弹窗；彻底重置红色入口（.danger-button）+
      双重确认（第二重手输「彻底重置」）+ 文件清单预览/已删列表 + 全视图刷新
- [x] `bash scripts/build.sh && bash scripts/build-gui.sh` 零错误
- [x] 静态自检：gui 源码无 SQL 字符串、无 import scoring.cli.*、无 System.out/System.in
- [x] Xvfb 实机走查：四维打分 3 条→直接结算（四维均 20/19/20/19、合计 78）、明细页补录启用/
      未面试置灰、附加 11 被拒→9 成功（+9 caiyi）、排名 12 列（87.00=78+9）、红色彻底重置入口可见

### 4.4 已知环境性限制（非代码缺陷）

- 本机无显示器且无中文输入法，Xvfb 自动化只能输入 ASCII：彻底重置的「手输彻底重置」与名单管理
  「新增候选人（中文姓名）」两步在 Linux 侧仅验证到对话框/按钮状态层面；两条输入逻辑与一期已验证的
  confirmTypedYes（YES 路径）共用同一实现，Windows 冒烟清单中已列为人工验证项。
- GTK 文件对话框的位置栏输入会被自动补全篡改（如 roster→rooster），导致无头环境偶发 NoSuchFile——
  仅影响自动化脚本，人工在 Windows 原生文件对话框中选择文件不受影响。

---

## 5. Windows 手工冒烟步骤清单（二期版）

> 前置：JDK 17 → `scripts\fetch-libs.bat`、`scripts\fetch-gui-libs.bat` → `scripts\build-gui.bat`
> → `scripts\run-gui.bat`。逐条打勾；样例见 `samples\`（名单样例-四维.xlsx / 名单样例-规整.txt）。

| # | 操作 | 预期结果 |
|---|---|---|
| 1 | 运行 `run-gui.bat` | 窗口打开；侧栏底部「初始化系统」为红字描边、「彻底重置（删除一切数据）」为**红色实底** |
| 2 | 「名单导入」→ 选 `samples\名单样例-四维.xlsx` | 弹窗：成功解析 10 行（新增 9、更新 1）/ 跳过 3 / 编码识别 `Excel(.xlsx)` |
| 3 | 再导入 `samples\名单样例-规整.txt` | 幂等：匹配学号仅更新姓名，报告与 §4.4 口径一致 |
| 4 | 「候选人」页 | 表格 9 人；底部「名单管理」卡出现；未选中时改名/改学号/删除**置灰** |
| 5 | 名单管理·新增：姓名「测试员」+ 学号 2099000001 → 新增 | 提示 ✓；列表出现该人（未面试） |
| 6 | 选中「测试员」→ 改名为「测试₂号」（任意新名）→ 改名 | 提示 ✓；表格姓名更新 |
| 7 | 选中 2099000001 → 改学号为 2099000002 → 确认 | 确认框提示级联迁移；成功后列表出现 2099000002 |
| 8 | 选中 2099000002 → 删除 → 确认 | 红色按钮；确认框提示级联删除；列表中消失 |
| 9 | 「面试打分」→ 选 2023000001 开始面试 | 打分界面出现**四个输入框**（责任心/时间管理能力/学生工作能力/部门契合度，0~25） |
| 10 | 输入 20 →回车→ 18.5 →回车→ 22 →回车→ 19 →回车 | 回车自动流转；末框回车保存；表格出现四维行；绿色提示含四维值 |
| 11 | 输入 26 → 保存 | 弹「分值需在 0~25 之间」（GUI 即时校验） |
| 12 | 再录 2 条（如 21/20/19/18 与 17/19/20/21） | 共 3 条记录 |
| 13 | 「结束评分并计算最终分」 | ≥3 条**无**二次确认，直接弹结算：四维均 20.00/19.00/20.00/19.00、四维合计 78.00、最终分 78.00 |
| 14 | 对第二人面试，只录 2 条 → 结束评分 | 弹「评分不足 3 条 — 二次确认」（列出各维普通平均）；先取消（仍面试中）再确认 → 按各维普通平均结算 |
| 15 | 明细页（双击已结束者）→ 补录四维（如 18/19/20/21） | 表单可输入（未面试/面试中者置灰并提示原因）；提交后汇总卡即时重算 |
| 16 | 附加分输 11 → 提交 | 被拦截：「附加分值需在 0~10 之间」 |
| 17 | 附加分改 9 + 原因「才艺加分」→ 提交 | ✓ 附加成功；附加表多一行；最终分 = 四维合计 + 9 |
| 18 | 「撤销」页 | 预览「给 X 添加附加分 9.00（才艺加分）」→ 撤销 → 附加消失、最终分回退 |
| 19 | 「排名」页 | 12 列（名次/姓名/学号/四维均×4/四维合计/平均方式/附加合计/最终分/记录数）；说明含「同分逐维比较」 |
| 20 | 「导出 CSV…」→ 全部 → 保存 → Excel 打开 | 13 列：排名/学号/姓名/四维×4/四维合计/平均方式/记录数/附加分明细(含理由)/附加合计/最终分；UTF-8 BOM |
| 21 | 「初始化系统」 | YES 门禁流程同一期；执行后名单保留、全部未面试 |
| 22 | 「彻底重置（删除一切数据）」 | ①文件清单预览（库文件+data 下 CSV）与统计风险 → ②可选导出留档 → ③普通确认 → ④不输「彻底重置」时按钮禁用，手输 `彻底重置` 后可点 → ⑤列出已删除文件，界面归零 |
| 23 | 彻底重置后各页 | 仪表盘全 0；候选人/排名页空（名单也清空）；可重新导入或用名单管理新增 |
