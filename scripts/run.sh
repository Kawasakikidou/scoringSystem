#!/usr/bin/env bash
# 启动 CLI（交互式中文菜单）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
JAR="$ROOT/lib/h2-2.2.224.jar"
if [ ! -f "$JAR" ]; then
    echo "缺少 H2 驱动，先执行：bash scripts/fetch-libs.sh" >&2
    exit 1
fi
if [ ! -d "$ROOT/out/scoring" ]; then
    echo "尚未编译，先执行：bash scripts/build.sh" >&2
    exit 1
fi
exec java -Dfile.encoding=UTF-8 -cp "$ROOT/out:$ROOT/lib/*" scoring.cli.Main "$@"
