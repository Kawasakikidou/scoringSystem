#!/usr/bin/env bash
# 下载 JavaFX 17 SDK（Gluon 官方）到 lib/openjfx-17/<platform>/
# 用法：bash scripts/fetch-gui-libs.sh [-x http://代理:端口] [linux|windows]
#   默认按当前平台下载；跨平台打包时可手工指定平台（如为 Windows 运行机下载 windows 包）。
#   脚本不写死任何代理；-x 可显式透传代理（本机无外网时用）。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JFX_VERSION="17.0.20"

PROXY=""
PLATFORM=""
while [ $# -gt 0 ]; do
    case "$1" in
        -x) PROXY="${2:?-x 需要代理地址参数}"; shift 2 ;;
        linux|windows) PLATFORM="$1"; shift ;;
        *) echo "未知参数：$1（用法：fetch-gui-libs.sh [-x 代理] [linux|windows]）" >&2; exit 1 ;;
    esac
done

if [ -z "$PLATFORM" ]; then
    case "$(uname -s)" in
        Linux) PLATFORM=linux ;;
        MINGW*|MSYS*|CYGWIN*|Windows_NT) PLATFORM=windows ;;
        *) echo "不支持的平台：$(uname -s)；请手工指定 linux 或 windows" >&2; exit 1 ;;
    esac
fi

case "$PLATFORM" in
    linux)   SUFFIX="linux-x64" ;;
    windows) SUFFIX="windows-x64" ;;
esac

DEST="$ROOT/lib/openjfx-17/$PLATFORM"            # 开发运行用 SDK（含本地库与 jar）
JMODS_DEST="$ROOT/lib/openjfx-17/$PLATFORM-jmods"  # 打包用 jmods（jlink/jpackage 需要）
URL="https://download2.gluonhq.com/openjfx/$JFX_VERSION/openjfx-${JFX_VERSION}_${SUFFIX}_bin-sdk.zip"
JMODS_URL="https://download2.gluonhq.com/openjfx/$JFX_VERSION/openjfx-${JFX_VERSION}_${SUFFIX}_bin-jmods.zip"

need_sdk=0; need_jmods=0
if [ ! -f "$DEST/lib/javafx.controls.jar" ] && [ ! -f "$DEST/lib/javafx.controls.jmod" ]; then
    need_sdk=1
fi
if [ ! -f "$JMODS_DEST/javafx.controls.jmod" ]; then
    need_jmods=1
fi
if [ "$need_sdk" -eq 0 ] && [ "$need_jmods" -eq 0 ]; then
    echo "已存在：$DEST 与 $JMODS_DEST"
    exit 0
fi
mkdir -p "$DEST" "$JMODS_DEST"

CURL_PROXY=()
WGET_PROXY=()
if [ -n "$PROXY" ]; then
    CURL_PROXY=(-x "$PROXY")
    WGET_PROXY=(-e "https_proxy=$PROXY")
fi

fetch() { # fetch <url> <outzip>
    echo "==> 验证下载地址（HEAD）：$1"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSI --max-time 60 "${CURL_PROXY[@]}" "$1" >/dev/null \
            || { echo "错误：下载地址不可达：$1" >&2; exit 1; }
        echo "==> 下载中……"
        curl -fL --retry 3 "${CURL_PROXY[@]}" -o "$2" "$1"
    elif command -v wget >/dev/null 2>&1; then
        wget -q --spider --tries=1 "${WGET_PROXY[@]}" "$1" \
            || { echo "错误：下载地址不可达：$1" >&2; exit 1; }
        echo "==> 下载中……"
        wget -q --tries=3 "${WGET_PROXY[@]}" -o "$2" "$1"
    else
        echo "错误：需要 curl 或 wget 来下载依赖。" >&2
        exit 1
    fi
}

extract() { # extract <zip> <dest>
    WORK="$(mktemp -d)"
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$1" -d "$WORK"
    elif command -v python3 >/dev/null 2>&1; then
        python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$1" "$WORK"
    else
        (cd "$WORK" && jar -xf "$1")
    fi
    TOP="$(ls -d "$WORK"/*/ 2>/dev/null | head -1)"
    rm -rf "$2"
    mkdir -p "$2"
    if [ -n "$TOP" ]; then
        cp -r "$TOP"/. "$2"/
    else
        cp -r "$WORK"/. "$2"/
    fi
    rm -rf "$WORK" "$1"
}

if [ "$need_sdk" -eq 1 ]; then
    fetch "$URL" "$DEST.openjfx.zip"
    extract "$DEST.openjfx.zip" "$DEST"
    echo "完成：$DEST/lib （开发运行：--module-path 指向该目录）"
fi

if [ "$need_jmods" -eq 1 ]; then
    fetch "$JMODS_URL" "$JMODS_DEST.openjfx.zip"
    extract "$JMODS_DEST.openjfx.zip" "$JMODS_DEST"
    echo "完成：$JMODS_DEST （打包：jpackage --module-path 指向该目录）"
fi
