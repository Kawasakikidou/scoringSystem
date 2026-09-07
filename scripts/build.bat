@echo off
rem 全量编译（Windows）：先单独编译 core 自证无 UI 依赖，再编译 cli
setlocal enabledelayedexpansion
set ROOT=%~dp0..
set OUT=%ROOT%\out
set CORE_OUT=%ROOT%\out\core-only

if exist "%CORE_OUT%" rmdir /s /q "%CORE_OUT%"
if not exist "%CORE_OUT%" mkdir "%CORE_OUT%"
if not exist "%OUT%" mkdir "%OUT%"

echo ==^> 第 1 步：core 独立编译
set CORE_SRCS=
for /r "%ROOT%\src\core\java" %%f in (*.java) do set CORE_SRCS=!CORE_SRCS! "%%f"
javac -encoding UTF-8 -d "%CORE_OUT%" %CORE_SRCS%
if errorlevel 1 exit /b 1

echo ==^> 第 2 步：全量编译 core + cli
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
set ALL_SRCS=
for /r "%ROOT%\src\core\java" %%f in (*.java) do set ALL_SRCS=!ALL_SRCS! "%%f"
for /r "%ROOT%\src\cli\java" %%f in (*.java) do set ALL_SRCS=!ALL_SRCS! "%%f"
javac -encoding UTF-8 -d "%OUT%" %ALL_SRCS%
if errorlevel 1 exit /b 1

echo 编译全部通过。运行：scripts\run.bat
endlocal
