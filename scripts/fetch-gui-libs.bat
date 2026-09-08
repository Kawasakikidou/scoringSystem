@echo off
rem 下载 JavaFX 17 SDK（Gluon 官方，Windows amd64）到 lib\openjfx-17\windows\
rem 用法：scripts\fetch-gui-libs.bat [-x http://代理:端口]
rem   脚本不写死任何代理；本机无外网时可用 -x 显式透传代理。Windows 10+ 自带 curl.exe。
setlocal
set ROOT=%~dp0..
set JFX_VERSION=17.0.20
set DEST=%ROOT%\lib\openjfx-17\windows
set JMODS_DEST=%ROOT%\lib\openjfx-17\windows-jmods
set URL=https://download2.gluonhq.com/openjfx/%JFX_VERSION%/openjfx-%JFX_VERSION%_windows-x64_bin-sdk.zip
set JMODS_URL=https://download2.gluonhq.com/openjfx/%JFX_VERSION%/openjfx-%JFX_VERSION%_windows-x64_bin-jmods.zip

set PROXY=
if "%~1"=="-x" set PROXY=%~2

set NEED_SDK=0
if not exist "%DEST%\lib\javafx.controls.jar" set NEED_SDK=1
set NEED_JMODS=0
if not exist "%JMODS_DEST%\javafx.controls.jmod" set NEED_JMODS=1
if "%NEED_SDK%"=="0" if "%NEED_JMODS%"=="0" (
    echo 已存在：%DEST% 与 %JMODS_DEST%
    exit /b 0
)
if not exist "%DEST%" mkdir "%DEST%"
if not exist "%JMODS_DEST%" mkdir "%JMODS_DEST%"

rem ---- 下载单个 zip 到目标目录并原地解包 ----
call :download_unpack "%URL%" "%DEST%" openjfx
if errorlevel 1 exit /b 1
echo 完成：%DEST%\lib （开发运行：--module-path 指向该目录）

call :download_unpack "%JMODS_URL%" "%JMODS_DEST%" openjfxjmods
if errorlevel 1 exit /b 1
echo 完成：%JMODS_DEST% （打包：jpackage --module-path 指向该目录）
exit /b 0

rem ==================== 子程序 ====================
:download_unpack
set SRCURL=%~1
set DSTDIR=%~2
set TAG=%~3
if exist "%DSTDIR%\%TAG%-done.txt" goto :eof
set TMPDIR=%TEMP%\%TAG%-%JFX_VERSION%-%RANDOM%%RANDOM%
mkdir "%TMPDIR%"
echo ==^> 验证下载地址（HEAD）：%SRCURL%
if defined PROXY (
    curl.exe -fsSI --max-time 30 -x "%PROXY%" "%SRCURL%" >nul 2>&1
) else (
    curl.exe -fsSI --max-time 30 "%SRCURL%" >nul 2>&1
)
if errorlevel 1 (
    echo 错误：下载地址不可达：%SRCURL%
    exit /b 1
)
echo ==^> 下载中……
if defined PROXY (
    curl.exe -fL --retry 3 -x "%PROXY%" -o "%TMPDIR%\%TAG%.zip" "%SRCURL%"
) else (
    curl.exe -fL --retry 3 -o "%TMPDIR%\%TAG%.zip" "%SRCURL%"
)
if errorlevel 1 (
    echo curl 失败，尝试 PowerShell 下载...
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%SRCURL%' -OutFile '%TMPDIR%\%TAG%.zip'"
    if errorlevel 1 (
        echo 下载失败：请手动下载 %SRCURL%
        exit /b 1
    )
)
echo ==^> 解压到 %DSTDIR% ...
powershell -NoProfile -Command "Expand-Archive -Path '%TMPDIR%\%TAG%.zip' -DestinationPath '%TMPDIR%' -Force"
if errorlevel 1 (
    echo 解压失败
    exit /b 1
)
for /d %%d in ("%TMPDIR%\javafx-sdk-*" "%TMPDIR%\javafx-jmods-*") do (
    if exist "%%~d" xcopy /E /I /Y "%%~d\*" "%DSTDIR%\" >nul
)
rmdir /s /q "%TMPDIR%"
echo done> "%DSTDIR%\%TAG%-done.txt"
goto :eof
