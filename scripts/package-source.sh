#!/usr/bin/env bash
# 生成「Windows 一键打包源包」：dist/ScoringGUI-win-package.zip
# 用法：bash scripts/package-source.sh
# 内容 = 项目源码 + 脚本 + 文档 + 样例 + H2 驱动（不含 out/、data/、openjfx 大目录、.git）。
# 在 Linux/macOS 上运行本脚本，把 zip 拷到 Windows，解压后双击 scripts\package-win.bat 即得 exe。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGE="$ROOT/dist/.stage/ScoringGUI-win-package"
OUT="$ROOT/dist/ScoringGUI-win-package.zip"

echo "==> 组装源包（staging: $STAGE）"
rm -rf "$ROOT/dist/.stage"
mkdir -p "$STAGE"
for item in src/core src/gui src/cli scripts docs samples lib/h2-2.2.224.jar README.md .gitignore data/.gitkeep.txt; do
    if [ -e "$ROOT/$item" ]; then
        cp -r --parents "$item" "$STAGE/" 2>/dev/null || { mkdir -p "$STAGE/$(dirname "$item")"; cp -r "$ROOT/$item" "$STAGE/$item"; }
    fi
done

cat > "$STAGE/请先读我.txt" <<'EOF'
【学生组织面试评分系统 GUI —— Windows 打包源包】
====================================================
1. 在 Windows 上安装「完整版」JDK 17（scoop/chocolatey/adoptium 均可；JRE 不行，
   需要 jpackage）。命令行验证：java -version 显示 17.0.x，where jpackage 有输出。
2. 双击 scripts\package-win.bat
   - 自动完成：环境检查 → 下载 H2 与 JavaFX 17 SDK/jmods（仅首次，共约 150MB）
     → javac 编译 core+gui → 组装 scoring-gui.jar → jpackage 生成安装器；
   - 无管理员权限要求（按用户安装）。
3. 产出：dist\ScoringGUI-1.0.0.exe —— 拷给任何 Windows 10/11 电脑即可双击安装，
   运行无黑框；目标机无需安装 Java（运行时已内置）。
4. 详细说明：docs\GUI打包指南-win.md；使用手册：docs\GUI使用说明.md；
   安装后请按 docs\GUI交付说明.md 第 3 节冒烟清单验证一遍。
5. 数据与备份：见 docs\GUI打包指南-win.md 第 5 节（默认 data\，程序 Files 下自动用用户目录）。
EOF

echo "==> 压缩为 $OUT"
python3 - "$STAGE" "$OUT" <<'PYEOF'
import os, sys, zipfile
stage, out = sys.argv[1], sys.argv[2]
base = os.path.dirname(stage)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(stage):
        dirs.sort(); files.sort()
        for f in files:
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, base))
print("完成：%s （%d 项）" % (out, sum(len(fs) for _, _, fs in os.walk(stage))))
PYEOF
rm -rf "$ROOT/dist/.stage"
ls -la "$OUT"
