# 学生组织面试评分系统（Java 17 后端 + CLI）

面向学生组织纳新的面试评分工具：导入报名名单（容忍乱序多列文本）→ 逐人面试打分 →
自动去极值算最终分 → 排名 → 导出 Excel 可读 CSV（UTF-8 BOM）。
core 层纯业务、零 UI 依赖，为未来 GUI 预留完整接入空间（接口见 `docs/接口文档.md`）。

## 目录

```
lib/       第三方 jar（H2 2.2.224，唯一外部依赖）
data/      运行期数据库 scoring.mv.db 与 CSV 导出（已 gitignore）
src/core/  core 层：ScoringService + DTO + 内部解析器/DAO（可无依赖独立编译）
src/cli/   中文交互 CLI 壳（仅依赖 core）
samples/   验收样例：规整名单 / 乱序多列含坏行名单
docs/      接口文档、CLI 使用说明、自测记录
scripts/   fetch-libs / build / run（sh 与 bat）
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

也可以手工编译（验收方式，JDK 17）：

```bash
# core 独立编译（证明无 UI/控制台依赖）
javac -encoding UTF-8 -d out/core-only $(find src/core -name '*.java')
# 全量编译（cli 依赖 core；H2 仅运行期需要）
javac -encoding UTF-8 -d out $(find src/core -name '*.java') $(find src/cli -name '*.java')
# 运行
java -Dfile.encoding=UTF-8 -cp out:lib/* scoring.cli.Main
```

## 数据与配置

- 数据库：`data/scoring.mv.db`（H2 嵌入式文件库），可用环境变量 `SCORING_DB_URL` 或
  `-Dscoring.db.url` 覆盖（默认 `jdbc:h2:file:data/scoring`，相对启动目录）。
- 默认名单文件：当前目录 `名单.txt`（菜单里可输入任意路径）。
- 全量规则/语义/限制请读 `docs/接口文档.md`；面向非技术使用者的操作手册为
  `docs/CLI使用说明.md`；验收流程与输出见 `docs/自测记录.md`。

## 验收样例

| 文件 | 预期（空库首次导入） |
|---|---|
| `samples/名单样例-规整.txt` | 成功 8 行（新增 8）/ 跳过 0 |
| `samples/名单样例-乱序.txt` | 成功 10 行（新增 9、更新 1）/ 跳过 3（手机号行、18 位连写学号行、无学号行） |
