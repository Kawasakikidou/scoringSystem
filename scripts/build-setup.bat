@echo off
rem ============================================================
rem 组装安装向导壳：把 dist\ScoringGUI-1.0.0.exe 嵌入向导壳，
rem 产出可交付的 dist\ScoringGUI-Setup-1.0.0.exe
rem   - 未安装：安装进度 -> 完成页（可勾选"删除安装包"/立即运行）
rem   - 已安装：管理页（启动 / 卸载 / 关闭）
rem 依赖：.NET Framework 4.8 的 csc.exe（Win10/11 自带）；
rem       需先运行 package-win.bat 或手动产出 dist\ScoringGUI-1.0.0.exe
rem 说明：本脚本及同目录 .bat 以 ANSI(GBK) 编码保存；SetupWizard.cs 为 UTF-8
rem ============================================================
chcp 936 >nul 2>&1
setlocal
set ROOT=%~dp0..
cd /d "%ROOT%"
set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
if not exist "%CSC%" (
  echo [失败] 找不到 csc.exe（需要 .NET Framework 4.x，Win10/11 自带）。
  exit /b 1
)
if not exist "%ROOT%\dist\ScoringGUI-1.0.0.exe" (
  echo [失败] 缺少 dist\ScoringGUI-1.0.0.exe，请先运行 scripts\package-win.bat。
  exit /b 1
)
echo ==^> 编译安装向导壳（嵌入安装引擎 30MB + 应用图标）...
"%CSC%" /nologo /target:winexe /codepage:65001 ^
  /win32icon:"%ROOT%\icons\ScoringGUI.ico" ^
  /out:"%ROOT%\dist\ScoringGUI-Setup-1.0.0.exe" ^
  /resource:"%ROOT%\dist\ScoringGUI-1.0.0.exe",ScoringGuiSetup.payload.bin ^
  "%ROOT%\scripts\SetupWizard.cs"
if errorlevel 1 (
  echo [失败] 向导壳编译失败
  exit /b 1
)
echo 完成：dist\ScoringGUI-Setup-1.0.0.exe（发给同事的安装向导壳）
echo       首次使用：双击 → 安装进度 → 完成页可勾选删除安装包；
echo       若已安装再次双击：提供 启动/卸载/关闭 管理页。
endlocal
