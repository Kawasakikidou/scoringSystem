@echo off
rem fetch-libs-v2.bat : download H2 + Apache POI 5.x (full set) into lib\
rem ASCII-only on purpose (works under any cmd codepage; no chcp needed)
rem usage : fetch-libs-v2.bat            (direct)
rem         fetch-libs-v2.bat -x http://127.0.0.1:7890   (via HTTP proxy)
setlocal
set ROOT=%~dp0..
set PROXY_OPT=
if "%1"=="-x" (
    if "%2"=="" ( echo usage: fetch-libs-v2.bat -x proxy-url & exit /b 1 )
    set PROXY_OPT=-x %2
)
if not exist "%ROOT%\lib" mkdir "%ROOT%\lib"
set BASE=https://repo1.maven.org/maven2

call :dl "%BASE%/com/h2database/h2/2.2.224/h2-2.2.224.jar" h2-2.2.224.jar
call :dl "%BASE%/org/apache/poi/poi/5.2.5/poi-5.2.5.jar" poi-5.2.5.jar
call :dl "%BASE%/org/apache/poi/poi-ooxml/5.2.5/poi-ooxml-5.2.5.jar" poi-ooxml-5.2.5.jar
call :dl "%BASE%/org/apache/poi/poi-ooxml-lite/5.2.5/poi-ooxml-lite-5.2.5.jar" poi-ooxml-lite-5.2.5.jar
call :dl "%BASE%/org/apache/xmlbeans/xmlbeans/5.2.0/xmlbeans-5.2.0.jar" xmlbeans-5.2.0.jar
call :dl "%BASE%/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar" commons-io-2.15.1.jar
call :dl "%BASE%/org/apache/commons/commons-compress/1.25.0/commons-compress-1.25.0.jar" commons-compress-1.25.0.jar
call :dl "%BASE%/org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar" commons-collections4-4.4.jar
call :dl "%BASE%/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar" commons-math3-3.6.1.jar
call :dl "%BASE%/commons-codec/commons-codec/1.16.1/commons-codec-1.16.1.jar" commons-codec-1.16.1.jar
call :dl "%BASE%/com/zaxxer/SparseBitSet/1.3/SparseBitSet-1.3.jar" SparseBitSet-1.3.jar
call :dl "%BASE%/org/apache/logging/log4j/log4j-api/2.21.1/log4j-api-2.21.1.jar" log4j-api-2.21.1.jar

echo All dependencies ready (H2 + POI 5.2.5). If direct download fails,
echo retry with:  fetch-libs-v2.bat -x http://127.0.0.1:7890
endlocal
exit /b 0

:dl
set URL=%~1
set OUT=%ROOT%\lib\%~2
if exist "%OUT%" ( echo exists: %OUT% & exit /b 0 )
echo HEAD check %URL% ...
curl.exe -sfIL -o NUL %PROXY_OPT% "%URL%"
if errorlevel 1 (
    echo cannot reach %URL% - check network or use -x proxy option
    exit /b 1
)
echo download %URL% ...
curl.exe -fL --retry 3 %PROXY_OPT% -o "%OUT%" "%URL%"
if errorlevel 1 (
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%OUT%'"
    if errorlevel 1 ( echo download failed: %URL% & exit /b 1 )
)
echo done: %OUT%
exit /b 0
