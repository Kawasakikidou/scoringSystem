#!/usr/bin/env bash
# 全量编译（二期：POI 5.x 进 core，classpath 需含 lib/*）
#   第 1 步：core 独立编译（classpath 仅 lib/* 的第三方库，不含任何 GUI/CLI 依赖 → 自证分层）
#   第 2 步：core + cli 全量编译
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/out"
CORE_OUT="$ROOT/out/core-only"

rm -rf "$CORE_OUT"
mkdir -p "$CORE_OUT" "$OUT"
CORE_SRCS=$(find "$ROOT/src/core/java" -name '*.java' | sort)
CLI_SRCS=$(find "$ROOT/src/cli/java" -name '*.java' | sort)

echo "==> 第 1 步：core 独立编译（classpath=lib/*，验证不含任何 GUI/CLI/控制台依赖）"
javac -encoding UTF-8 -cp "$ROOT/lib/*" -d "$CORE_OUT" $CORE_SRCS
echo "    完成：$CORE_OUT"

echo "==> 第 2 步：全量编译 core + cli"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$ROOT/lib/*" -d "$OUT" $CORE_SRCS $CLI_SRCS
echo "    完成：$OUT"

echo "编译全部通过。运行方式："
echo "  bash scripts/run.sh            （交互菜单）"
echo "  bash scripts/run.sh import samples/名单样例-规整.txt"
