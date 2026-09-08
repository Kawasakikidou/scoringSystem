# GUI 变更步骤（二期：四维评分 / 名单管理 / Excel / 彻底重置）

> 适用范围：JavaFX GUI（`src/gui/java/scoring/gui/`）。二期只改 core/cli/scripts/docs/samples，
> **本文件是 GUI 改造的唯一指引**；改造前 GUI 依赖一期 API，与二期 core 不匹配（build-gui 会编译失败）
> 属预期——按本文逐条改造后恢复编译。
> 业务规则一律在 core（ScoringService）中，GUI 只做调用、即时校验与展示，禁止复刻第二套。

---

## 0. 现状与总体影响

二期 core 变更点：评分从“单值”变为“四维（r/t/s/f）”；新增名单 CRUD 与 Excel 导入；
附加分单笔上限 10；排名同分逐维比较；CSV 列扩充；新增 `resetEverything()` 与迁移提示。
GUI 所有**打分/补录表单、评分明细列、排名列、导出列、DTO 字段绑定、统计文案**均受影响。

## 1. 依赖与启动

- `scripts/build-gui.sh / run-gui.sh(.bat)` **无需改动**（POI 仅 core 使用，classpath 的
  `lib/*` 通配已含 POI；GUI 依赖 core 重新编译即可）。
- core 改动后先跑 `bash scripts/build.sh` 确认 core+cli 编译通过，再 `bash scripts/build-gui.sh`。

## 2. 打分表单（InterviewController）

- 旧：单个分值输入框（0~100，`ScoreInputValidator`），保存调
  `addInterviewScore(studentNo, BigDecimal)`。
- 新：四个输入框，中文标签 = `ScoringService.DIM_LABELS[]`（责任心/时间管理能力/学生工作能力/
  部门契合度），各自即时校验 0~25、两位小数（GUI 校验与服务端一致，服务端仍兜底）；
  保存调 `addInterviewScore(studentNo, r, t, s, f)`（新签名）。
- 「删除上一条」逻辑不变（`deleteLastInterviewScore`），展示删除记录的四维值。
- 「结束评分」：`finishInterview(no, false)` 返回 `FinishResult`——用新字段
  rAvg/tAvg/sAvg/fAvg/dimensionTotal/bonusTotal/finalScore 展示；`!completed && belowThree`
  弹「不足 3 条将按普通平均（四维各自普通平均）」二次确认 → `finishInterview(no, true)`。
- 候选人信息区显示已录“四维评分记录 N 条”。

## 3. 补录与附加表单（DetailController 等）

- 补录表单：旧单值 → 新四维四个输入框（0~25），调 `addMakeupScore(no, r, t, s, f)`；
  仅 FINISHED 可点（否则提示原因：未面试请先面试 / 面试中请直接打分）。
- 附加分表单：上限提示与校验 0~100 → **0~10**（`ScoringService.BONUS_MAX`），原因仍必填。
- 明细区（普通评分表）改为“四维评分记录表”：列 = 序号/责任心/时间管理能力/学生工作能力/
  部门契合度/录入时间；平均值区显示四维平均与 `dimensionTotal`。

## 4. 候选人列表/详情与新增“名单管理”入口

- 候选人列表（CandidatesController）列不变（学号/姓名/状态/完成时间）；点击行进明细。
- 详情页新增“名单管理”操作组：新增（姓名+学号）、改名、改学号、删除按钮；
  删除/改号需确认对话框并提示**级联影响**（评分记录/附加分/日志一并迁移或删除）；
  面试中者删除与改号由 core 抛错（展示 `getMessage()` 即可），GUI 前置置灰更佳。
- 删除成功后刷新列表、明细与仪表盘。

## 5. 排名表与导出（RankingController）

- 排名表列：名次/姓名/学号/**责任心均/时间管理均/学生工作均/部门契合均/四维合计**/平均方式/
  附加合计/最终分/记录数（对应 RankRow 新字段 rAvg…fAvg/dimensionTotal/normalScoreCount）。
- 表头或图例注明排序规则：最终分降序 → 同分逐维比较（责任心→时间管理→学生工作→部门契合）→
  学号升序。
- 导出按钮对话框选项不变（仅已完成/全部）；`exportCsv` 签名不变但列内容已按二期列序输出
  （§5.4 接口文档），GUI 无需拼列。

## 6. 签名与 DTO 变更对照表（核心）

| 位置 | 一期（旧） | 二期（新） |
|---|---|---|
| ScoringService.MAX_VALUE | 100（总分概念） | 语义改为 25（单维上限）；新增 `DIM_MAX=25`、`BONUS_MAX=10`、`DIM_LABELS[4]` |
| addInterviewScore | (no, BigDecimal) | (no, BigDecimal r, t, s, f) |
| addMakeupScore | (no, BigDecimal) | (no, BigDecimal r, t, s, f) |
| finishInterview 返回值 | average/finalScore | rAvg/tAvg/sAvg/fAvg/dimensionTotal/finalScore |
| deleteLastInterviewScore 返回值 | ScoreItem(value) | ScoreItem(r,t,s,f) |
| ScoreItem | value | r, t, s, f |
| CandidateDetail | average | rAvg,tAvg,sAvg,fAvg,dimensionTotal |
| RankRow | average | rAvg,tAvg,sAvg,fAvg,dimensionTotal |
| OpLogEntry | value | r,t,s,f,amount（撤销预览改文案） |
| BonusItem | （字段不变）amount | 语义上限 10（表单提示文案改） |
| addBonus | amount≤100 | amount≤10（服务端抛错文案 0~10） |
| 新增 API | — | addCandidate / updateCandidateName / updateCandidateStudentNo / deleteCandidate / migrationNotice / resetTargetPreview / resetEverything |
| importRoster | 文本 | 同签名；新增 xlsx/xls（魔数识别）支持，ImportReport.encoding 出现 Excel 标签 |
| CSV | 一期列 | 二期 13 列（见接口文档 §5.4） |
| 统计文案 | 普通评分 N 条 | 四维评分记录 N 条（SystemStats.scoreCount 语义=记录条数） |

DTO 字段变更对绑定的影响：凡表格列/表单使用 `ScoreItem.value()`、
`CandidateDetail.average()`、`RankRow.average()`、`FinishResult.average()` 处，全部换新字段。

## 7. 仪表盘统计口径

- 卡片数量不变（总数/未面试/面试中/已结束/评分条数/附加分条数）；
- “评分条数”文案改为“四维评分记录条数”（scoreCount = score 表行数 = 完整四维记录数）；
- 启动流程：构造服务后先读 `migrationNotice()`（非空弹提示“旧库已自动迁移：旧评分未换算”），
  再 `currentInterviewing()` 弹“恢复上次未完成面试”。

## 8. 新增“彻底重置”入口（危险操作）

- 位置：与“初始化系统”并列但明显区隔——菜单项/按钮用**红色危险样式**（app.css 增加
  `.danger-button`，与普通按钮区分），文案“彻底重置（删除一切数据）”。
- 流程（与接口文档 §8.6 一致）：
  1. 调 `resetTargetPreview()`，对话框列出将删除的文件（数据库/伴生文件/数据目录 CSV）与
     统计风险（名单 N 人/记录 N 条）；
  2. 可选“先导出留档”（复用导出对话框，范围=全部）；
  3. 第一次确认（普通确认框）；
  4. **第二次确认要求手输确认词 `彻底重置`**（区别于初始化的 YES，防混淆）；
  5. `resetEverything()` → 展示已删除文件列表 → **刷新全部视图**（仪表盘归零、列表清空、
     排名清空、候选人区回到全新空状态）；
  6. 之后程序可正常重新导入（库已原地重建）。
- 保留“初始化系统”入口（保留名单、清评分）：颜色用普通警示而非红色，避免混淆。

## 附：改造自检清单（GUI）

- [ ] `grep -r "\.value()\|\.average()" src/gui` 无残留（除新字段名 avgX 外）
- [ ] 打分/补录表单 = 四输入（0~25 即时校验）
- [ ] 附加分提示 0~10；原因必填
- [ ] 排名/明细/导出按二期列展示
- [ ] 名单管理增/改/删带确认与级联提示
- [ ] 迁移提示与恢复面试弹窗；彻底重置红色入口 + 双重确认（手输确认词）+ 全视图刷新
- [ ] `bash scripts/build.sh && bash scripts/build-gui.sh` 零错误
