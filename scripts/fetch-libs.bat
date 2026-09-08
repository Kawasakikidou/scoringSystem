@echo off
rem 下载 H2 驱动到 lib\（Windows 10+ 自带 curl.exe；否则用 PowerShell 下载）
setlocal
set ROOT=%~dp0..
set H2_VERSION=2.2.224
set JAR=%ROOT%\lib\h2-%H2_VERSION%.jar
if not exist "%ROOT%\lib" mkdir "%ROOT%\lib"
if exist "%JAR%" (
    echo 已存在：%JAR%
    exit /b 0
)
echo 下载 H2 驱动（Maven Central）...
curl.exe -fL --retry 3 -o "%JAR%" "https://repo1.maven.org/maven2/com/h2database/h2/%H2_VERSION%/h2-%H2_VERSION%.jar"
if errorlevel 1 (
    echo curl 失败，尝试 PowerShell 下载...
    powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/h2database/h2/%H2_VERSION%/h2-%H2_VERSION%.jar' -OutFile '%JAR%'"
    if errorlevel 1 (
        echo 下载失败：请手动下载 h2-%H2_VERSION%.jar 放入 lib\ 目录
        exit /b 1
    )
)
echo 完成：%JAR%
endlocal
