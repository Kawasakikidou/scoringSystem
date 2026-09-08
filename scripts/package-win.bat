@echo off
rem ============================================================
rem 学生组织面试评分系统 GUI —— 一键打包为 Windows exe (jpackage)
rem 用法：scripts\package-win.bat        （在 Windows 打包机上运行）
rem 前置：JDK 17（完整版，含 jpackage 工具）；首次打包需要联网下载组件。
rem 产出：dist\ScoringGUI-1.0.0.exe （安装器，双击即装，无控制台黑框）
rem 详细说明见 docs\GUI打包指南-win.md
rem
rem 注意：本脚本（及同目录其他 .bat）以系统 ANSI（GBK）编码保存，
rem       与 cmd 默认代码页一致，双击可直接运行。请勿另存为 UTF-8，
rem       否则中文注释/提示在 GBK 控制台下会被误解析导致命令错乱。
rem ============================================================
setlocal EnableDelayedExpansion
set ROOT=%~dp0..
cd /d "%ROOT%"
set APP_NAME=ScoringGUI
set APP_VERSION=1.0.0
set DEST=%ROOT%\dist
set JMODS=%ROOT%\lib\openjfx-17\windows-jmods
set WIX=%ROOT%\lib\wix3

echo ============================================================
echo   一键打包：%APP_NAME% v%APP_VERSION%  (Windows)
echo ============================================================

rem ---- 第 0 步：环境检查（JDK 17 + jpackage；缺失时自动从注册表补 PATH）----
java -version 2>&1 | findstr /b "java" | findstr "17\.0" >nul
if errorlevel 1 (
  echo [失败] 需要 JDK 17（java -version 应显示 17.0.x）。请先安装 JDK17 并加入 PATH。
  exit /b 1
)
where jpackage >nul 2>&1
if errorlevel 1 (
  echo   jpackage 不在 PATH，尝试从 JDK 注册表自动定位 bin 目录...
  set "JH="
  for /f "tokens=2,*" %%a in ('reg query "HKLM\SOFTWARE\JavaSoft\JDK" /s /v JavaHome 2^>nul ^| findstr /i "JavaHome"') do set "JH=%%b"
  if defined JH if exist "!JH!\bin\jpackage.exe" set "PATH=!JH!\bin;!PATH!"
)
where jpackage >nul 2>&1
if errorlevel 1 (
  echo [失败] 找不到 jpackage。请安装"完整版"JDK 17（JRE 不够），或把 JDK17 的 bin 目录加入 PATH 后重试。
  exit /b 1
)
echo [1/6] 环境检查通过：JDK 17 + jpackage

rem ---- 第 1 步：依赖（H2 驱动 + JavaFX 17 SDK，已存在自动跳过）----
if not exist "%ROOT%\lib\h2-2.2.224.jar" (
  echo [2/6] 下载 H2 驱动...
  call scripts\fetch-libs.bat || (echo [失败] H2 下载失败 & exit /b 1)
) else echo [2/6] H2 驱动已就绪
if not exist "%ROOT%\lib\openjfx-17\windows\lib\javafx.controls.jar" if not exist "%JMODS%\javafx.controls.jmod" (
  echo [2/6] 下载 JavaFX 17 SDK 与 jmods（Windows，约 150MB，仅首次）...
  call scripts\fetch-gui-libs.bat || (echo [失败] JavaFX 下载失败 & exit /b 1)
) else if not exist "%JMODS%\javafx.controls.jmod" (
  echo [2/6] 补下载 JavaFX 17 jmods（打包需要）...
  call scripts\fetch-gui-libs.bat || (echo [失败] jmods 下载失败 & exit /b 1)
) else echo [2/6] JavaFX 17（SDK+jmods，windows）已就绪

rem ---- 第 2 步：编译 core + gui（javac 直编，含复制 CSS 资源）----
echo [3/6] 编译 core + gui ...
call scripts\build-gui.bat || (echo [失败] 编译失败 & exit /b 1)

rem ---- 第 3 步：收集打包输入（scoring-gui.jar + h2 驱动）----
echo [4/6] 组装应用 jar...
if exist "%ROOT%\dist-pkg" rmdir /s /q "%ROOT%\dist-pkg"
mkdir "%ROOT%\dist-pkg"
jar --create --file "%ROOT%\dist-pkg\scoring-gui.jar" --main-class scoring.gui.Main -C out .
if errorlevel 1 (echo [失败] jar 打包失败 & exit /b 1)
copy /y "%ROOT%\lib\h2-2.2.224.jar" "%ROOT%\dist-pkg\" >nul
if errorlevel 1 (echo [失败] 复制 H2 失败 & exit /b 1)
echo        输入目录 dist-pkg\ ：scoring-gui.jar + h2-2.2.224.jar

rem ---- 第 4 步：WiX 工具（jpackage 生成 exe 安装器必需；缺失时自动下载到 lib\wix3）----
if not exist "%WIX%\light.exe" (
  echo [5/6] 未找到 WiX 工具，自动下载 WiX 3.14 到 lib\wix3（约 40MB，仅首次）...
  if not exist "%WIX%" mkdir "%WIX%"
  set "WIXZIP=%TEMP%\wix314-binaries.zip"
  curl.exe -fL --retry 3 -o "!WIXZIP!" "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
  if errorlevel 1 (
    echo [失败] WiX 下载失败。可手动从 https://github.com/wixtoolset/wix3/releases
    echo        下载 wix314-binaries.zip 解压到 lib\wix3\ 后重试。
    exit /b 1
  )
  powershell -NoProfile -Command "Expand-Archive -Path '!WIXZIP!' -DestinationPath '!WIX!' -Force"
  if errorlevel 1 (echo [失败] WiX 解压失败 & exit /b 1)
)
if not exist "%WIX%\light.exe" (
  echo [失败] lib\wix3 下找不到 light.exe，WiX 组件不完整。请检查 lib\wix3 后重试。
  exit /b 1
)
set "PATH=%WIX%;%PATH%"
echo [5/6] WiX 工具就绪

rem ---- 第 5 步：jpackage 生成无黑框 exe 安装器 ----
rem 注意：不加 --win-console —— 它是无值开关，加上即变成"控制台应用"；
rem      缺省即 GUI 子系统，运行时不出现命令行黑框（--win-console=false 写法
rem      在新版 jpackage 会报"无效选项"）。
echo [6/6] jpackage 打包安装器（GUI 子系统，无控制台黑框）...
if not exist "%DEST%" mkdir "%DEST%"
jpackage --type exe --name %APP_NAME% --app-version %APP_VERSION% --vendor "ScoringGUI" ^
  --input "%ROOT%\dist-pkg" --main-jar scoring-gui.jar --main-class scoring.gui.Main ^
  --module-path "%JMODS%" --add-modules javafx.controls,javafx.fxml,java.sql,java.logging,java.management,java.naming ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --icon "%ROOT%\icons\ScoringGUI.ico" ^
  --dest "%DEST%" ^
  --win-shortcut --win-per-user-install
if errorlevel 1 (
  echo [失败] jpackage 打包出错。常见原因：jpackage 版本与 JDK 不一致 /
  echo        磁盘空间不足 / 旧的安装包仍被占用（先删 dist\ 下同名 exe）/
  echo        Windows Installer 或 .NET 组件异常。
  exit /b 1
)

rem ---- 第 7 步：组装安装向导壳（推荐交付物，完成页可勾选删除安装包）----
call scripts\build-setup.bat || (echo [警告] 向导壳组装失败，可稍后单独运行 scripts\build-setup.bat & exit /b 1)

echo.
echo ============================================================
echo   打包完成：%DEST%\%APP_NAME%-%APP_VERSION%.exe （安装引擎）
echo   推荐交付：%DEST%\%APP_NAME%-Setup-%APP_VERSION%.exe （安装向导壳）
echo   向导壳：双击安装，完成后可勾选删除安装包；已安装时再双击
echo         提供 启动/卸载/关闭 管理页（卸载走标准 Windows 向导）。
echo   安装引擎仅供重打包或特殊场景；发给同事请用 Setup 版。
echo   双击即装（无需管理员权限，桌面快捷方式）。
echo   数据位置：默认在 启动目录的 data\ 下；若安装在不可写目录
echo   （如 C:\Program Files\），会自动改用 用户主目录\.scoring-gui\。
echo   建议安装完后先按 docs\GUI使用说明.md 冒烟一次。
echo ============================================================
endlocal
