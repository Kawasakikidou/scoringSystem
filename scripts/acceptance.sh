#!/usr/bin/env bash
# 二期一键验收脚本：环境检查 → 依赖下载(H2+POI) → 生成 Excel 样例 → core独立编译+全量编译
#               → 端到端自测（txt/xlsx/xls 导入、CRUD、四维、排名、CSV、撤销、迁移、彻底重置）→ 汇总
# 用法：
#   bash scripts/acceptance.sh                      直连下载依赖
#   bash scripts/acceptance.sh -x http://127.0.0.1:7890   依赖下载走 HTTP 代理（不写死）
# 前置：JDK 17（java/javac）；python3 + openpyxl/xlwt（生成 Excel 验收样例；自动优先用项目 .venv，缺失时按提示装配）
# 退出码：0=全部通过；1=任一环节失败（含环境缺失）
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Python 解释器：优先使用项目自带 venv（.venv，含 openpyxl/xlwt），否则回退 PATH 中的 python3
PY_BIN="$ROOT/.venv/bin/python3"
[ -x "$PY_BIN" ] || PY_BIN="$(command -v python3 || true)"

PROXY_ARGS=()
if [ "${1:-}" = "-x" ]; then
    PROXY_ARGS=(-x "${2:?用法：acceptance.sh -x <代理URL>}")
fi

echo "=================================================="
echo "  学生组织面试评分系统 —— 二期一键验收"
echo "=================================================="

# ---------- 第 0 步：环境检查 ----------
echo "环境检查："
command -v java >/dev/null 2>&1 || { echo "  ✗ 未找到 java，需要 OpenJDK 17 LTS"; exit 1; }
command -v javac >/dev/null 2>&1 || { echo "  ✗ 未找到 javac（仅装 JRE 不够，需要 JDK 17）"; exit 1; }
command -v curl >/dev/null 2>&1 || command -v wget >/dev/null 2>&1 \
    || { echo "  ✗ 未找到 curl/wget（下载依赖需要）"; exit 1; }
echo "  ✓ $(java -version 2>&1 | head -n 1)"
if [ -z "$PY_BIN" ]; then
    echo "  ✗ 未找到 python3（生成 Excel 验收样例需要），请先装配 venv："
    echo "      python3 -m venv .venv && .venv/bin/pip install openpyxl xlwt"
    exit 1
fi
if [ -x "$ROOT/.venv/bin/python3" ]; then
    PY_SRC="项目 venv（.venv）"
else
    PY_SRC="PATH 中的 python3"
fi
echo "  ✓ python3 $("$PY_BIN" --version 2>&1)（$PY_SRC）"
if ! "$PY_BIN" -c "import openpyxl, xlwt" >/dev/null 2>&1; then
    echo "  ✗ python3 缺少 openpyxl/xlwt 库。"
    echo "    Debian/Ubuntu（PEP 668 限制 pip 全局安装）任选其一："
    echo "      A. 装配项目 venv：python3 -m venv .venv && .venv/bin/pip install openpyxl xlwt"
    echo "      B. sudo apt install python3-openpyxl python3-xlwt"
    echo "      C. pip install --break-system-packages openpyxl xlwt"
    exit 1
fi
echo "  ✓ python3 含 openpyxl/xlwt"

step() {
    echo
    echo "---------- 第 $1 步：$2 ----------"
}

# ---------- 第 1 步：依赖下载（H2 + POI 5.2.5 系） ----------
step 1 "下载依赖 lib/（H2 + Apache POI；已存在自动跳过）"
if ! bash scripts/fetch-libs.sh "${PROXY_ARGS[@]}"; then
    echo
    echo "✗ 依赖下载失败。请检查网络，或带代理重试："
    echo "  bash scripts/acceptance.sh -x http://127.0.0.1:7890"
    exit 1
fi

# ---------- 第 2 步：生成 Excel 验收样例 ----------
step 2 "生成 samples/名单样例-四维.xlsx 与 .xls（内容对齐乱序 txt 口径）"
if ! "$PY_BIN" scripts/gen-excel-samples.py; then
    echo "✗ Excel 样例生成失败，见上方输出。"
    exit 1
fi
if [ ! -f "samples/名单样例-四维.xlsx" ] || [ ! -f "samples/名单样例-四维.xls" ]; then
    echo "✗ 样例文件未生成完整（xlsx/xls 缺一）。"
    exit 1
fi

# ---------- 第 3 步：编译 ----------
step 3 "编译：core 独立编译（-cp lib/*，自证无 GUI/CLI 依赖）+ core+cli 全量 javac"
if ! bash scripts/build.sh; then
    echo
    echo "✗ 编译失败：请按上方 javac 报错修复（可将完整输出贴回给开发者）。"
    exit 1
fi

# ---------- 第 4 步：端到端自测 ----------
step 4 "端到端自测：导入统计→CRUD→四维去极值→同分排序→附加/撤销→CSV→初始化→旧库迁移→彻底重置"
bash scripts/self-test.sh
RC=$?

echo
echo "=================================================="
if [ "$RC" -eq 0 ]; then
    echo "  验收结果：通过（self-test 全部断言通过，退出码 0）"
else
    echo "  验收结果：未通过（见上方 ✗ 失败项）"
fi
echo "  会话日志副本：$ROOT/out/e2e-logs/（可整体贴回排查）"
echo "=================================================="
exit "$RC"
