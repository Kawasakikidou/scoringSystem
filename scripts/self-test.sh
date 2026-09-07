#!/usr/bin/env bash
# 端到端自测（验收用）：隔离临时数据库，走通
#   导入(规整+幂等) → 搜索 → 面试(≥3 去极值 / 不足3条二次确认) → 暂存续面(跨进程)
#   → 排名 → 导出 CSV(BOM) → 补录 → 附加 → 撤销 → 初始化 → 名单保留
# 用法：先 bash scripts/fetch-libs.sh && bash scripts/build.sh，然后 bash scripts/self-test.sh
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
JAR="$ROOT/lib/h2-2.2.224.jar"
[ -f "$JAR" ] || { echo "缺少 lib/h2-2.2.224.jar：请先执行 bash scripts/fetch-libs.sh"; exit 2; }
[ -d "$ROOT/out/scoring" ] || { echo "尚未编译：请先执行 bash scripts/build.sh"; exit 2; }

TMPD="$(mktemp -d)"
export SCORING_DB_URL="jdbc:h2:file:$TMPD/e2e"
export JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
RUN_CMD=( "$JAVA_BIN" -Dfile.encoding=UTF-8 -cp "$ROOT/out:$ROOT/lib/*" scoring.cli.Main )
LOG1="$TMPD/run1.log"; LOG2="$TMPD/run2.log"; LOG3="$TMPD/run3.log"
PASS=0; FAILED=0

pass() { echo "  ✓ $1"; PASS=$((PASS+1)); }
fail() { echo "  ✗ $1"; FAILED=$((FAILED+1)); }
check() { # $1=log $2=预期子串 $3=说明
  if grep -qF -- "$2" "$1"; then pass "$3"; else fail "$3（日志不含：$2）"; fi
}

echo "==== 1) 非交互导入：规整样例（预期 成功8/新增8/跳过0） ===="
"${RUN_CMD[@]}" import samples/名单样例-规整.txt > "$TMPD/imp1.log" 2>&1
grep -qF "成功解析 8 行（新增 8 人、更新/去重 0 行）" "$TMPD/imp1.log" && pass "首次导入规整 8/8/0" || { fail "首次导入统计"; cat "$TMPD/imp1.log"; }
grep -qF "跳过 0 行" "$TMPD/imp1.log" && pass "跳过 0" || fail "跳过统计"

echo "==== 2) 乱序样例导入统计（独立库，预期 成功10/新增9/更新1/跳过3） ===="
SCORING_DB_URL="jdbc:h2:file:$TMPD/messy" "${RUN_CMD[@]}" import samples/名单样例-乱序.txt > "$TMPD/imp-messy.log" 2>&1
grep -qF "成功解析 10 行（新增 9 人、更新/去重 1 行）" "$TMPD/imp-messy.log" && pass "乱序样例 10/9/1" || { fail "乱序样例统计"; cat "$TMPD/imp-messy.log"; }
grep -qF "跳过 3 行" "$TMPD/imp-messy.log" && pass "乱序样例跳过 3 行" || fail "乱序跳过统计"
grep -qF "跳过示例" "$TMPD/imp-messy.log" && pass "打印跳过样例（≤3 条）" || fail "跳过样例展示"

echo "==== 3) 重复导入幂等（预期 新增0/更新8，不产生重复记录） ===="
"${RUN_CMD[@]}" import samples/名单样例-规整.txt > "$TMPD/imp2.log" 2>&1
grep -qF "成功解析 8 行（新增 0 人、更新/去重 8 行）" "$TMPD/imp2.log" && pass "重复导入幂等 0/8" || { fail "重复导入统计"; cat "$TMPD/imp2.log"; }

echo "==== 3) 交互完整流程（第 1 会话） ===="
printf '%s\n' \
  2 王小明 1 1 85 1 90 1 80 3 \
  2 2023000102 1 1 70 1 90 3 y \
  2 麦麦提 1 1 82 1 86 1 91 3 \
  4 "" \
  5 1 data/e2e.csv \
  6 李小红 1 95 n \
  7 王小明 1 5 才艺加分 n \
  8 y \
  2 2023000106 1 1 90 0 \
  0 | "${RUN_CMD[@]}" > "$LOG1" 2>&1
check "$LOG1" "面试已完成。" "面试 A（王小明，3 条去极值）完成"
check "$LOG1" "平均分：80.00（普通平均，评分不足 3 条经确认）" "面试 B（李小红，2 条）经二次确认按普通平均"
check "$LOG1" "最终分：86.00" "面试 C（麦麦提·吐尔逊，去极值）86.00"
check "$LOG1" "补录成功：现共 3 条普通评分。" "补录 B（+95 → 3 条重算）"
check "$LOG1" "最终分：90.00" "补录后 B 最终分 90.00"
check "$LOG1" "附加成功：附加分合计 5.00。" "附加 A（+5 才艺加分）"
check "$LOG1" "已撤销「添加附加分 5.00（才艺加分）」" "撤销最近一次附加"
check "$LOG1" "已暂存。该候选人仍处于“面试中”" "暂存退出（D 陈静 1 条评分）"

echo "==== 4) CSV 导出检查（UTF-8 BOM + 3 行已完成） ===="
BOM=$(od -An -tx1 -N3 data/e2e.csv | tr -d ' \n')
if [ "$BOM" = "efbbbf" ]; then pass "CSV 头为 UTF-8 BOM (EF BB BF)"; else fail "CSV 无 BOM（实际 $BOM）"; fi
head -1 data/e2e.csv | grep -q "排名,学号,姓名,状态,最终分" && pass "CSV 表头正确" || fail "CSV 表头"
grep -q "2023000102" data/e2e.csv && pass "CSV 含已完成候选人明细" || fail "CSV 明细"
rm -f data/e2e.csv

echo "==== 5) 跨进程恢复面试（第 2 会话：续面陈静并结束） ===="
printf '%s\n' 2 y 1 80 3 y 0 | "${RUN_CMD[@]}" > "$LOG2" 2>&1
check "$LOG2" "正在面试：陈静" "自动检测未完成面试并续面"
check "$LOG2" "最终分：85.00" "续面完成（2 条二次确认，普通平均 85.00）"

echo "==== 6) 初始化（第 3 会话：跳过备份 + YES 门禁） ===="
printf '%s\n' 9 n YES 3 "" 0 | "${RUN_CMD[@]}" > "$LOG3" 2>&1
check "$LOG3" "初始化完成：全部评分与面试状态已清空" "初始化执行成功"
check "$LOG3" "共显示 8 人（总数 8 人）。" "名单保留 8 人"
RESET_COUNT=$(grep -E "^202300010[1-8] " "$LOG3" | grep -c "未面试")
[ "$RESET_COUNT" = "8" ] && pass "8 人均回到未面试" || fail "状态重置（未面试行数=$RESET_COUNT）"

echo
echo "======== 自测结果：通过 $PASS 项，失败 $FAILED 项 ========"
mkdir -p "$ROOT/out/e2e-logs"
cp "$LOG1" "$LOG2" "$LOG3" "$TMPD/imp1.log" "$TMPD/imp2.log" "$TMPD/imp-messy.log" "$ROOT/out/e2e-logs/" 2>/dev/null
echo "运行日志副本：out/e2e-logs/（imp1/imp2/imp-messy/run1/run2/run3）"
rm -rf "$TMPD"
[ "$FAILED" -eq 0 ]
