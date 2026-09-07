@echo off
rem 启动 CLI（中文交互菜单）。Windows 控制台请先执行：chcp 65001
setlocal
set ROOT=%~dp0..
cd /d "%ROOT%"
if not exist "%ROOT%\lib\h2-2.2.224.jar" (
    echo 缺少 H2 驱动，先执行：scripts\fetch-libs.bat
    exit /b 1
)
if not exist "%ROOT%\out\scoring" (
    echo 尚未编译，先执行：scripts\build.bat
    exit /b 1
)
java -Dfile.encoding=UTF-8 -cp "%ROOT%\out;%ROOT%\lib\*" scoring.cli.Main %*
endlocal
