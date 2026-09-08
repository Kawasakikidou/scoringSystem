#!/usr/bin/env bash
# 启动 GUI（工作目录 = 项目根，数据库落在 data/）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

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
if [ ! -d "$ROOT/out/scoring/gui" ]; then
    echo "尚未编译，先执行：bash scripts/build-gui.sh" >&2
    exit 1
fi

exec java -Dfile.encoding=UTF-8 \
    --module-path "$JFX_LIB" --add-modules javafx.controls,javafx.fxml \
    -cp "$ROOT/out:$ROOT/lib/*" scoring.gui.Main "$@"
