#!/usr/bin/env bash
# 下载第三方 jar 到 lib/（H2 驱动 + Apache POI 5.x 读取 xls/xlsx 所需全部 jar）
# 用法：
#   bash scripts/fetch-libs.sh                    直连下载
#   bash scripts/fetch-libs.sh -x http://127.0.0.1:7890   经 HTTP 代理下载（不写死本机代理）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$ROOT/lib"

PROXY=""
if [ "${1:-}" = "-x" ]; then
    PROXY="${2:?用法：fetch-libs.sh -x <代理URL>}"
    shift 2
fi

BASE="https://repo1.maven.org/maven2"
# path 与 输出文件名（文件名即 lib 中 jar 名）
declare -A FILES=(
    ["com/h2database/h2/2.2.224/h2-2.2.224.jar"]="h2-2.2.224.jar"
    ["org/apache/poi/poi/5.2.5/poi-5.2.5.jar"]="poi-5.2.5.jar"
    ["org/apache/poi/poi-ooxml/5.2.5/poi-ooxml-5.2.5.jar"]="poi-ooxml-5.2.5.jar"
    ["org/apache/poi/poi-ooxml-lite/5.2.5/poi-ooxml-lite-5.2.5.jar"]="poi-ooxml-lite-5.2.5.jar"
    ["org/apache/xmlbeans/xmlbeans/5.2.0/xmlbeans-5.2.0.jar"]="xmlbeans-5.2.0.jar"
    ["commons-io/commons-io/2.15.1/commons-io-2.15.1.jar"]="commons-io-2.15.1.jar"
    ["org/apache/commons/commons-compress/1.25.0/commons-compress-1.25.0.jar"]="commons-compress-1.25.0.jar"
    ["org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar"]="commons-collections4-4.4.jar"
    ["org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar"]="commons-math3-3.6.1.jar"
    ["commons-codec/commons-codec/1.16.1/commons-codec-1.16.1.jar"]="commons-codec-1.16.1.jar"
    ["com/zaxxer/SparseBitSet/1.3/SparseBitSet-1.3.jar"]="SparseBitSet-1.3.jar"
    ["org/apache/logging/log4j/log4j-api/2.21.1/log4j-api-2.21.1.jar"]="log4j-api-2.21.1.jar"
)

# URL 先 HEAD 验证存在（直连失败可加 -x 代理重试）
check_url() {
    local url="$1"
    if command -v curl >/dev/null 2>&1; then
        if [ -n "$PROXY" ]; then curl -sfIL -o /dev/null -x "$PROXY" "$url"
        else curl -sfIL -o /dev/null "$url"; fi
    elif command -v wget >/dev/null 2>&1; then
        if [ -n "$PROXY" ]; then
            wget --spider -S -e "use_proxy=yes" -e "http_proxy=$PROXY" -e "https_proxy=$PROXY" "$url" 2>/dev/null
        else wget --spider -S "$url" 2>/dev/null; fi
    else
        echo "错误：需要 curl 或 wget 来下载依赖。" >&2; exit 1
    fi
}

download() {
    local url="$1" out="$ROOT/lib/$2"
    if [ -f "$out" ]; then
        echo "已存在：$out"
        return 0
    fi
    check_url "$url"
    echo "下载 $url ..."
    if command -v curl >/dev/null 2>&1; then
        if [ -n "$PROXY" ]; then curl -fL --retry 3 -x "$PROXY" -o "$out" "$url"
        else curl -fL --retry 3 -o "$out" "$url"; fi
    else
        if [ -n "$PROXY" ]; then
            wget -q --tries=3 -e "use_proxy=yes" -e "http_proxy=$PROXY" -e "https_proxy=$PROXY" -O "$out" "$url"
        else wget -q --tries=3 -O "$out" "$url"; fi
    fi
    echo "完成：$out"
}

for path in "${!FILES[@]}"; do
    download "$BASE/$path" "${FILES[$path]}"
done
echo "全部依赖就绪（H2 + POI 5.2.5 系，共 ${#FILES[@]} 个 jar）。"
echo "说明：若直连失败，请带代理重试：bash scripts/fetch-libs.sh -x http://127.0.0.1:7890"
