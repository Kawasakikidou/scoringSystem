@echo off
rem ȫ������ core + gui��Windows����Ҫ JDK 17��javac �� PATH��
setlocal enabledelayedexpansion
set ROOT=%~dp0..
set OUT=%ROOT%\out
set JFX_LIB=%ROOT%\lib\openjfx-17\windows\lib

if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo ȱ�� JavaFX SDK����ִ�У�scripts\fetch-gui-libs.bat
    exit /b 1
)
if not exist "%ROOT%\lib\h2-2.2.224.jar" (
    echo ȱ�� H2 ��������ִ�У�scripts\fetch-libs.bat
    exit /b 1
)

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

set ALL_SRCS=
for /r "%ROOT%\src\core\java" %%f in (*.java) do set ALL_SRCS=!ALL_SRCS! "%%f"
for /r "%ROOT%\src\gui\java" %%f in (*.java) do set ALL_SRCS=!ALL_SRCS! "%%f"

echo ==^> ���� core + gui��JavaFX SDK 17��
javac -encoding UTF-8 -d "%OUT%" -cp "%OUT%;%ROOT%\lib\*;%JFX_LIB%\*" %ALL_SRCS%
if errorlevel 1 exit /b 1

echo ==^> ���� GUI ��Դ��CSS���� out\
xcopy /E /I /Y "%ROOT%\src\gui\resources\*" "%OUT%\" >nul

echo ����ͨ�������У�scripts\run-gui.bat
endlocal
