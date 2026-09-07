#!/usr/bin/env bash
# 一键验收脚本：环境检查 → 依赖下载 → core独立编译+全量编译 → 端到端自测(13组/22条断言) → 汇总
# 用法：bash scripts/acceptance.sh
# 退出码：0=全部通过；1=依赖/编译/自测任一环节失败（含环境缺失）
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "=================================================="
echo "  学生组织面试评分系统 —— 一键验收"
echo "=================================================="

# ---------- 第 0 步：环境检查 ----------
echo "环境检查："
if ! command -v java >/dev/null 2>&1; then
    echo "  ✗ 未找到 java，需要 OpenJDK 17 LTS"
    exit 1
fi
if ! command -v javac >/dev/null 2>&1; then
    echo "  ✗ 未找到 javac（仅装 JRE 不够，需要 JDK 17）"
    exit 1
fi
JAVA_VER="$(java -version 2>&1 | head -n 1)"
echo "  ✓ $JAVA_VER"

step() {
    echo
    echo "---------- 第 $1 步：$2 ----------"
}

# ---------- 第 1 步：依赖下载 ----------
step 1 "下载依赖 lib/h2-2.2.224.jar（已存在则自动跳过）"
if ! bash scripts/fetch-libs.sh; then
    echo
    echo "✗ 依赖下载失败。请检查网络，或手动从 Maven Central 下载"
    echo "  h2-2.2.224.jar 放入 lib/ 后重新运行本脚本。"
    exit 1
fi

# ---------- 第 2 步：编译 ----------
step 2 "编译：core 独立编译（自证无 UI/控制台依赖）+ core/cli 全量 javac"
if ! bash scripts/build.sh; then
    echo
    echo "✗ 编译失败：请按上方 javac 报错修复（可将完整输出贴回给开发者）。"
    exit 1
fi

# ---------- 第 3 步：端到端自测 ----------
step 3 "端到端自测：13 组验收点 / 22 条断言（导入→面试→排名→CSV→补录/附加/撤销→续面→初始化）"
bash scripts/self-test.sh
RC=$?

echo
echo "=================================================="
if [ "$RC" -eq 0 ]; then
    echo "  验收结果：通过（22 条断言全部通过，退出码 0）"
else
    echo "  验收结果：未通过（见上方 ✗ 失败项）"
fi
echo "  会话日志副本：$ROOT/out/e2e-logs/（run1/run2/run3/imp*.log，排查可整体贴回）"
echo "=================================================="
exit "$RC"
