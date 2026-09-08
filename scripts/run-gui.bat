@echo off
rem 启动 GUI（工作目录 = 项目根，数据库落在 data\）。
rem 要求：JDK 17（java 在 PATH 中；java -version 应显示 17.x）。
rem 控制台中文显示先执行 chcp 65001（本脚本已自动设置）。
chcp 65001 >nul
setlocal
set ROOT=%~dp0..
cd /d "%ROOT%"
set JFX_LIB=%ROOT%\lib\openjfx-17\windows\lib

if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo 缺少 JavaFX SDK，先执行：scripts\fetch-gui-libs.bat
    exit /b 1
)
if not exist "%ROOT%\lib\h2-2.2.224.jar" (
    echo 缺少 H2 驱动，先执行：scripts\fetch-libs.bat
    exit /b 1
)
if not exist "%ROOT%\out\scoring\gui" (
    echo 尚未编译，先执行：scripts\build-gui.bat
    exit /b 1
)

java -Dfile.encoding=UTF-8 --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%ROOT%\out;%ROOT%\lib\*" scoring.gui.Main %*
endlocal
