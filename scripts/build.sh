#!/usr/bin/env bash
# 全量编译：先单独编译 core（自证 core 无任何 cli/UI 依赖），再编译 cli
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/out"
CORE_OUT="$ROOT/out/core-only"

rm -rf "$CORE_OUT"
mkdir -p "$CORE_OUT" "$OUT"
CORE_SRCS=$(find "$ROOT/src/core/java" -name '*.java' | sort)
CLI_SRCS=$(find "$ROOT/src/cli/java" -name '*.java' | sort)

echo "==> 第 1 步：core 独立编译（classpath 不含任何 jar，验证无 UI/控制台依赖）"
javac -encoding UTF-8 -d "$CORE_OUT" $CORE_SRCS
echo "    完成：$CORE_OUT"

echo "==> 第 2 步：全量编译 core + cli"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" $CORE_SRCS $CLI_SRCS
echo "    完成：$OUT"

echo "编译全部通过。运行方式："
echo "  bash scripts/run.sh            （交互菜单）"
echo "  bash scripts/run.sh import samples/名单样例-规整.txt"
