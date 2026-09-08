#!/usr/bin/env bash
# 全量编译 core + gui（javac 直接编译，不引入 Maven/Gradle）
# classpath 含 out、lib/h2-2.2.224.jar、lib/openjfx-17/<platform>/lib/*
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/out"

case "$(uname -s)" in
    Linux) PLATFORM=linux ;;
    MINGW*|MSYS*|CYGWIN*|Windows_NT) PLATFORM=windows ;;
    *) echo "不支持的平台：$(uname -s)" >&2; exit 1 ;;
esac
JFX_LIB="$ROOT/lib/openjfx-17/$PLATFORM/lib"

if [ ! -f "$JFX_LIB/javafx.controls.jar" ]; then
    echo "缺少 JavaFX SDK，先执行：bash scripts/fetch-gui-libs.sh" >&2
    exit 1
fi
if [ ! -f "$ROOT/lib/h2-2.2.224.jar" ]; then
    echo "缺少 H2 驱动，先执行：bash scripts/fetch-libs.sh" >&2
    exit 1
fi

CORE_SRCS=$(find "$ROOT/src/core/java" -name '*.java' | sort)
GUI_SRCS=$(find "$ROOT/src/gui/java" -name '*.java' | sort)

echo "==> 编译 core + gui（平台 $PLATFORM，JavaFX SDK 17）"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
    -cp "$OUT:$ROOT/lib/*:$JFX_LIB/*" \
    $CORE_SRCS $GUI_SRCS

echo "==> 复制 GUI 资源（CSS）到 out/"
cp -r "$ROOT/src/gui/resources/." "$OUT/"

echo "编译通过。运行方式："
echo "  bash scripts/run-gui.sh"
