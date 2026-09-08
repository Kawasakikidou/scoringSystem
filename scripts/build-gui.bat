@echo off
rem 全量编译 core + gui（Windows；需要 JDK 17，javac 在 PATH）
setlocal enabledelayedexpansion
set ROOT=%~dp0..
set OUT=%ROOT%\out
set JFX_LIB=%ROOT%\lib\openjfx-17\windows\lib

if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo 缺少 JavaFX SDK，先执行：scripts\fetch-gui-libs.bat
    exit /b 1
)
if not exist "%ROOT%\lib\h2-2.2.224.jar" (
    echo 缺少 H2 驱动，先执行：scripts\fetch-libs.bat
    exit /b 1
)

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

set ALL_SRCS=
for /r "%ROOT%\src\core\java" %%f in (*.java) do set ALL_SRCS=!ALL_SRCS! "%%f"
for /r "%ROOT%\src\gui\java" %%f in (*.java) do set ALL_SRCS=!ALL_SRCS! "%%f"

echo ==^> 编译 core + gui（JavaFX SDK 17）
javac -encoding UTF-8 -d "%OUT%" -cp "%OUT%;%ROOT%\lib\h2-2.2.224.jar;%JFX_LIB%\*" %ALL_SRCS%
if errorlevel 1 exit /b 1

echo ==^> 复制 GUI 资源（CSS）到 out\
xcopy /E /I /Y "%ROOT%\src\gui\resources\*" "%OUT%\" >nul

echo 编译通过。运行：scripts\run-gui.bat
endlocal
