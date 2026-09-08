#!/usr/bin/env bash
# 二期端到端自测：隔离临时数据库，覆盖
#   txt/xlsx/xls 导入统计 → 幂等 → 名单 CRUD(增/改名/改学号迁移/删除级联) → 四维打分
#   (逐维去极值/不足3条二次确认/同分逐维排序) → 附加分 11 拒 9 收 → 撤销/重做 →
#   CSV(BOM+列+理由) → 初始化 → 旧库自动迁移 → 彻底重置(确认词门禁+文件删除+重建可用)
# 前置：bash scripts/fetch-libs.sh && bash scripts/build.sh
#       && python3 有 openpyxl/xlwt（生成 Excel 样例）或先跑 bash scripts/gen-excel-samples.py
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[ -f "$ROOT/lib/h2-2.2.224.jar" ] || { echo "缺少 lib/h2-2.2.224.jar：请先 bash scripts/fetch-libs.sh"; exit 2; }
[ -d "$ROOT/out/scoring" ] || { echo "尚未编译：请先 bash scripts/build.sh"; exit 2; }

# Python 解释器：优先使用项目自带 venv（.venv，含 openpyxl/xlwt），否则回退 PATH 中的 python3
PY_BIN="$ROOT/.venv/bin/python3"
[ -x "$PY_BIN" ] || PY_BIN="$(command -v python3 || true)"

TMPD="$(mktemp -d)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
RUN_CMD=( "$JAVA_BIN" -Dfile.encoding=UTF-8 -cp "$ROOT/out:$ROOT/lib/*" scoring.cli.Main )
PASS=0; FAILED=0

pass() { echo "  ✓ $1"; PASS=$((PASS+1)); }
fail() { echo "  ✗ $1"; FAILED=$((FAILED+1)); }
check() { if grep -qF -- "$2" "$1"; then pass "$3"; else fail "$3（日志不含：$2）"; fi; }
interactive() { # $1=日志 $2...=输入行
  local log="$1"; shift
  printf '%s\n' "$@" | SCORING_DB_URL="$E2E_URL" "${RUN_CMD[@]}" > "$log" 2>&1
}

E2E_URL="jdbc:h2:file:$TMPD/e2e"
EXP="$TMPD/导出.csv"

echo "==== 0) Excel 验收样例生成 ===="
if [ -z "$PY_BIN" ]; then
  fail "缺少 python3 / 项目 .venv（需要 openpyxl/xlwt 生成 Excel 样例）"
else
  if "$PY_BIN" scripts/gen-excel-samples.py > "$TMPD/gen.log" 2>&1; then
    pass "生成 xlsx/xls 样例"
  else
    fail "生成 Excel 样例失败（需 .venv/bin/pip install openpyxl xlwt）；输出："; cat "$TMPD/gen.log"
  fi
fi

echo "==== 1) txt 规整样例导入与幂等 ===="
SCORING_DB_URL="$E2E_URL" "${RUN_CMD[@]}" import samples/名单样例-规整.txt > "$TMPD/imp1.log" 2>&1
grep -qF "成功解析 8 行（新增 8 人、更新/去重 0 行）" "$TMPD/imp1.log" && pass "txt 规整 8/8/0" || { fail "txt 规整统计"; cat "$TMPD/imp1.log"; }
SCORING_DB_URL="$E2E_URL" "${RUN_CMD[@]}" import samples/名单样例-规整.txt > "$TMPD/imp2.log" 2>&1
grep -qF "成功解析 8 行（新增 0 人、更新/去重 8 行）" "$TMPD/imp2.log" && pass "txt 幂等 0/8" || { fail "txt 幂等"; cat "$TMPD/imp2.log"; }

echo "==== 2) txt 乱序样例统计（口径不回归） ===="
SCORING_DB_URL="jdbc:h2:file:$TMPD/messy-txt" "${RUN_CMD[@]}" import samples/名单样例-乱序.txt > "$TMPD/imp-messy.log" 2>&1
grep -qF "成功解析 10 行（新增 9 人、更新/去重 1 行）" "$TMPD/imp-messy.log" && pass "txt 乱序 10/9/1" || fail "txt 乱序统计"
grep -qF "跳过 3 行" "$TMPD/imp-messy.log" && pass "txt 乱序跳过 3" || fail "txt 乱序跳过"

echo "==== 3) xlsx / xls 导入（统计口径与 txt 一致） ===="
if [ -f samples/名单样例-四维.xlsx ]; then
  SCORING_DB_URL="jdbc:h2:file:$TMPD/messy-xlsx" "${RUN_CMD[@]}" import samples/名单样例-四维.xlsx > "$TMPD/imp-xlsx.log" 2>&1
  grep -qF "成功解析 10 行（新增 9 人、更新/去重 1 行）" "$TMPD/imp-xlsx.log" && pass "xlsx 10/9/1（数值学号无科学计数法）" || { fail "xlsx 统计"; cat "$TMPD/imp-xlsx.log"; }
  grep -qF "Excel(.xlsx)" "$TMPD/imp-xlsx.log" && pass "xlsx 编码标签" || fail "xlsx 标签"
else
  fail "缺少 samples/名单样例-四维.xlsx（先跑 0 生成）"
fi
if [ -f samples/名单样例-四维.xls ]; then
  SCORING_DB_URL="jdbc:h2:file:$TMPD/messy-xls" "${RUN_CMD[@]}" import samples/名单样例-四维.xls > "$TMPD/imp-xls.log" 2>&1
  grep -qF "成功解析 10 行（新增 9 人、更新/去重 1 行）" "$TMPD/imp-xls.log" && pass "xls 10/9/1" || { fail "xls 统计"; cat "$TMPD/imp-xls.log"; }
  grep -qF "Excel(.xls)" "$TMPD/imp-xls.log" && pass "xls 编码标签" || fail "xls 标签"
else
  fail "缺少 samples/名单样例-四维.xls"
fi

echo "==== 4) 名单管理：新增（会话1） ===="
L="$TMPD/r1.log"; interactive "$L" 10 1 欧阳·娜 2023990001 0
check "$L" "已新增：欧阳·娜（2023990001）" "手动新增候选人"

echo "==== 5) 四维面试 X：1 条记录 + 不足3条二次确认（会话2） ===="
L="$TMPD/r2.log"; interactive "$L" 2 2023990001 1 1 10 10 10 10 3 y 0
check "$L" "四维合计：40.00（普通平均，评分记录不足 3 条经确认）" "X 不足 3 条二次确认（普通平均）"
check "$L" "最终分：40.00" "X 最终分 40.00"

echo "==== 6) 名单管理：改名 + 改学号（带评分迁移，会话3） ===="
L="$TMPD/r3.log"; interactive "$L" 10 2 欧阳·娜 1 欧阳娜娜 3 2023990001 1 2023990002 0
check "$L" "已改名：欧阳娜娜（2023990001）" "手动改名"
check "$L" "已改学号：由 2023990001 改为 2023990002" "改学号并级联迁移"

echo "==== 7) 迁移验证：改号后明细仍有 1 条评分（会话4） ===="
L="$TMPD/r4.log"; interactive "$L" 4 2023990002 "" 0
check "$L" "四维评分记录（1 条）" "级联迁移后评分仍在"
check "$L" "最终分：40.00" "迁移后最终分不变"

echo "==== 8) 四维去极值面试（会话5：A/B/C/D） ===="
L="$TMPD/r5.log"; interactive "$L" \
  2 王小明 1 1 20 18 22 16 1 24 10 12 20 1 16 20 25 10 1 20 16 14 22 3 \
  2 2023000102 1 1 10 10 10 10 1 20 20 20 20 3 y \
  2 麦麦提 1 1 25 17 21 13 1 24 16 20 12 1 23 15 19 11 3 \
  2 阿依古丽 1 1 21 21 21 13 1 20 20 20 12 1 19 19 19 11 3 \
  0
check "$L" "四维合计：73.00" "A 王小明 4 条逐维去极值 = 20+17+18+18"
check "$L" "责任心 20.00 ｜ 时间管理能力 17.00 ｜ 学生工作能力 18.00 ｜ 部门契合度 18.00" "A 四维平均明细"
check "$L" "四维合计：60.00（普通平均，评分记录不足 3 条经确认）" "B 李小红 2 条二次确认普通平均"
check "$L" "四维合计：72.00" "C/D 同分 72（用于排序判定）"

echo "==== 9) 排名顺序/附加上限/撤销/导出/删除级联/初始化（会话6） ===="
L="$TMPD/r6.log"; interactive "$L" \
  4 "" \
  7 王小明 1 11 上限测试 \
  7 王小明 1 9 才艺加分 n \
  8 y \
  7 王小明 1 9 才艺加分 n \
  5 1 "$EXP" \
  10 4 2023990002 1 y 0 \
  9 n YES \
  0
check "$L" "排序规则：最终分降序" "排名页输出"
grep -E '^    1 .*王小明' "$L" >/dev/null && pass "排名 1=王小明(73+9=82)" || fail "排名第 1 名"
grep -E '^    2 .*麦麦提·吐尔逊' "$L" >/dev/null && pass "同分 72 按责任心优先：C 第 2" || fail "同分排序 C"
grep -E '^    3 .*阿依古丽·买买提' "$L" >/dev/null && pass "同分 72：D 第 3" || fail "同分排序 D"
check "$L" "需在 0～10 之间" "附加 11 分被拒"
check "$L" "附加成功：附加分合计 9.00" "附加 9 分成功"
check "$L" "已撤销「添加附加分 9.00（才艺加分）」" "撤销附加并重算"
check "$L" "最新最终分：73.00" "撤销后最终分回落"
check "$L" "已删除候选人及其全部评分数据" "删除带评分候选人（级联）"
check "$L" "初始化完成" "初始化（保留名单）"
BOM=$(od -An -tx1 -N3 "$EXP" | tr -d ' \n')
[ "$BOM" = "efbbbf" ] && pass "CSV BOM" || fail "CSV BOM"
head -1 "$EXP" | grep -q "排名,学号,姓名,责任心,时间管理能力,学生工作能力,部门契合度,四维合计,平均方式,评分记录数,附加分明细,附加分合计,最终分" && pass "CSV 二期列头" || fail "CSV 列头"
grep -q "才艺加分" "$EXP" && pass "CSV 含附加分理由" || fail "CSV 理由"

echo "==== 10) 旧库自动迁移（v1 标量 points 库） ===="
cat > "$TMPD/legacy_v1.sql" <<'SQL'
CREATE TABLE candidate (student_no VARCHAR(10) PRIMARY KEY, name VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING', finished_at TIMESTAMP,
  imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE score (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  student_no VARCHAR(10) NOT NULL, points DECIMAL(6,2) NOT NULL CHECK (points >= 0 AND points <= 100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE bonus (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  student_no VARCHAR(10) NOT NULL, amount DECIMAL(6,2) NOT NULL CHECK (amount >= 0 AND amount <= 100),
  reason VARCHAR(200) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE op_log (seq BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  student_no VARCHAR(10) NOT NULL, op_type VARCHAR(12) NOT NULL, ref_id BIGINT NOT NULL,
  points DECIMAL(6,2), reason VARCHAR(200), occurred_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO candidate (student_no, name) VALUES ('9999999999', '旧库人');
INSERT INTO score (student_no, points, created_at) VALUES ('9999999999', 88.00, CURRENT_TIMESTAMP);
INSERT INTO bonus (student_no, amount, reason) VALUES ('9999999999', 5.00, '旧附加');
INSERT INTO op_log (student_no, op_type, ref_id, points, created_at) VALUES ('9999999999', 'ADD_SCORE', 1, 88.00, CURRENT_TIMESTAMP);
SQL
"$JAVA_BIN" -cp "$ROOT/lib/h2-2.2.224.jar" org.h2.tools.RunScript \
  -url "jdbc:h2:file:$TMPD/legacy" -user sa -password "" -script "$TMPD/legacy_v1.sql" > "$TMPD/legacy-run.log" 2>&1
if [ -f "$TMPD/legacy.mv.db" ]; then pass "已构造一期旧库"; else fail "构造旧库失败"; cat "$TMPD/legacy-run.log"; fi
L="$TMPD/r7.log"
SCORING_DB_URL="jdbc:h2:file:$TMPD/legacy" "${RUN_CMD[@]}" import samples/名单样例-规整.txt > "$L" 2>&1
check "$L" "【迁移提示】" "旧库迁移提示输出"
check "$L" "score_legacy_v1 / op_log_legacy_v1" "旧表归档命名"
check "$L" "成功解析 8 行（新增 8 人、更新/去重 0 行）" "迁移后新库可正常导入"
L="$TMPD/r8.log"
printf '%s\n' 3 "" 0 | SCORING_DB_URL="jdbc:h2:file:$TMPD/legacy" "${RUN_CMD[@]}" > "$L" 2>&1
check "$L" "共显示 9 人（总数 9 人）" "迁移保留旧候选人名单（旧库人+8）"

echo "==== 11) 彻底重置：确认词门禁 + 文件删除 + 重建可用 ===="
[ -f "$EXP" ] && pass "重置前置：存在导出 CSV" || fail "前置 CSV 缺失"
L="$TMPD/r9.log"; interactive "$L" 11 n y YES 0
check "$L" "确认词不符，已取消彻底重置" "错误确认词被拒"
L="$TMPD/r10.log"; interactive "$L" 11 n y 彻底重置 0
check "$L" "系统已重置为全新状态" "彻底重置完成"
grep -q "导出.csv" "$L" && pass "重置删除清单含 CSV" || fail "重置文件清单"
L="$TMPD/r11.log"; interactive "$L" 3 "" 0
check "$L" "共显示 0 人（总数 0 人）" "重置后名单归零（全新状态）"
[ ! -e "$EXP" ] && pass "重置后 CSV 已删除" || fail "CSV 未删除"
SCORING_DB_URL="$E2E_URL" "${RUN_CMD[@]}" import samples/名单样例-规整.txt > "$TMPD/imp-after.log" 2>&1
grep -qF "成功解析 8 行（新增 8 人、更新/去重 0 行）" "$TMPD/imp-after.log" && pass "重置后系统可正常重新导入" || { fail "重置后导入"; cat "$TMPD/imp-after.log"; }

echo
echo "======== 二期自测结果：通过 $PASS 项，失败 $FAILED 项 ========"
mkdir -p "$ROOT/out/e2e-logs"
cp "$TMPD"/imp*.log "$TMPD"/r*.log "$TMPD"/legacy-run.log "$TMPD"/gen.log "$ROOT/out/e2e-logs/" 2>/dev/null
echo "日志副本：out/e2e-logs/（imp*/r*.log）"
rm -rf "$TMPD"
[ "$FAILED" -eq 0 ]
