#!/usr/bin/env bash
# 下载 H2 驱动到 lib/（第三方 jar 统一放 lib/）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
H2_VERSION="2.2.224"
JAR="$ROOT/lib/h2-${H2_VERSION}.jar"
URL="https://repo1.maven.org/maven2/com/h2database/h2/${H2_VERSION}/h2-${H2_VERSION}.jar"

mkdir -p "$ROOT/lib"
if [ -f "$JAR" ]; then
    echo "已存在：$JAR"
    exit 0
fi
echo "下载 $URL ..."
if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "$JAR" "$URL"
elif command -v wget >/dev/null 2>&1; then
    wget -q --tries=3 -O "$JAR" "$URL"
else
    echo "错误：需要 curl 或 wget 来下载依赖。" >&2
    exit 1
fi
echo "完成：$JAR"
