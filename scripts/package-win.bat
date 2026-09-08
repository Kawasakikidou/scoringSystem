@echo off
rem ============================================================
rem 学生组织面试评分系统 GUI —— 一键打包为 Windows exe (jpackage)
rem 用法：scripts\package-win.bat        （在 Windows 打包机上运行）
rem 前置：JDK 17（完整版，含 jpackage 工具）；首次打包需要联网下载组件。
rem 产出：dist\ScoringGUI-1.0.0.exe （安装器，双击即装，无控制台黑框）
rem 详细说明见 docs\GUI打包指南-win.md
rem ============================================================
chcp 65001 >nul
setlocal
set ROOT=%~dp0..
cd /d "%ROOT%"
set APP_NAME=ScoringGUI
set APP_VERSION=1.0.0
set DEST=%ROOT%\dist
set JMODS=%ROOT%\lib\openjfx-17\windows-jmods

echo ============================================================
echo   一键打包：%APP_NAME% v%APP_VERSION%  (Windows)
echo ============================================================

rem ---- 第 0 步：环境检查（JDK 17 + jpackage）----
java -version 2>&1 | findstr /b "java" | findstr "17\.0" >nul
if errorlevel 1 (
  echo [失败] 需要 JDK 17（java -version 应显示 17.0.x）。请先安装 JDK17 并加入 PATH。
  exit /b 1
)
where jpackage >nul 2>&1
if errorlevel 1 (
  echo [失败] 找不到 jpackage。请安装“完整版”JDK 17（JRE 不够），或把 JDK17\bin 加入 PATH。
  exit /b 1
)
echo [1/5] 环境检查通过：JDK 17 + jpackage

rem ---- 第 1 步：依赖（H2 驱动 + JavaFX 17 SDK，已存在自动跳过）----
if not exist "%ROOT%\lib\h2-2.2.224.jar" (
  echo [2/5] 下载 H2 驱动...
  call scripts\fetch-libs.bat || (echo [失败] H2 下载失败 & exit /b 1)
) else echo [2/5] H2 驱动已就绪
if not exist "%ROOT%\lib\openjfx-17\windows\lib\javafx.controls.jar" if not exist "%JMODS%\javafx.controls.jmod" (
  echo [2/5] 下载 JavaFX 17 SDK 与 jmods（Windows，约 150MB，仅首次）...
  call scripts\fetch-gui-libs.bat || (echo [失败] JavaFX 下载失败 & exit /b 1)
) else if not exist "%JMODS%\javafx.controls.jmod" (
  echo [2/5] 补下载 JavaFX 17 jmods（打包需要）...
  call scripts\fetch-gui-libs.bat || (echo [失败] jmods 下载失败 & exit /b 1)
) else echo [2/5] JavaFX 17（SDK+jmods，windows）已就绪

rem ---- 第 2 步：编译 core + gui（javac 直编，含复制 CSS 资源）----
echo [3/5] 编译 core + gui ...
call scripts\build-gui.bat || (echo [失败] 编译失败 & exit /b 1)

rem ---- 第 3 步：收集打包输入（scoring-gui.jar + h2 驱动）----
echo [4/5] 组装应用 jar...
if exist "%ROOT%\dist-pkg" rmdir /s /q "%ROOT%\dist-pkg"
mkdir "%ROOT%\dist-pkg"
jar --create --file "%ROOT%\dist-pkg\scoring-gui.jar" --main-class scoring.gui.Main -C out .
if errorlevel 1 (echo [失败] jar 打包失败 & exit /b 1)
copy /y "%ROOT%\lib\h2-2.2.224.jar" "%ROOT%\dist-pkg\" >nul
if errorlevel 1 (echo [失败] 复制 H2 失败 & exit /b 1)
echo        输入目录 dist-pkg\ ：scoring-gui.jar + h2-2.2.224.jar

rem ---- 第 4 步：jpackage 生成无黑框 exe 安装器 ----
echo [5/5] jpackage 打包安装器（--win-console=false 无控制台黑框）...
if not exist "%DEST%" mkdir "%DEST%"
jpackage --type exe --name %APP_NAME% --app-version %APP_VERSION% --vendor "ScoringGUI" ^
  --input "%ROOT%\dist-pkg" --main-jar scoring-gui.jar --main-class scoring.gui.Main ^
  --module-path "%JMODS%" --add-modules javafx.controls,javafx.fxml,java.sql,java.logging,java.management,java.naming ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --dest "%DEST%" ^
  --win-console=false --win-shortcut --win-per-user-install
if errorlevel 1 (
  echo [失败] jpackage 打包出错。常见原因：jpackage 版本与 JDK 不一致 / 磁盘空间不足 / 安装包名冲突。
  exit /b 1
)

echo.
echo ============================================================
echo   打包完成：%DEST%\%APP_NAME%-%APP_VERSION%.exe
echo   双击即装（无需管理员权限，开始菜单/桌面有快捷方式）。
echo   数据位置：默认在 启动目录的 data\ 下；若安装在不可写目录
echo   （如 C:\Program Files\），会自动改用 用户主目录\.scoring-gui\。
echo   建议安装完后先按 docs\GUI使用说明.md 冒烟一次。
echo ============================================================
endlocal
