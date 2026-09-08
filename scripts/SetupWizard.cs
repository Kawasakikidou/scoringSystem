using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace ScoringGuiSetup
{
    internal static class Program
    {
        internal static readonly string ProductCode = "{59DE26EC-58BF-3022-B38E-00C22115D718}";
        internal static readonly string Squashed = "CE62ED95FB8522033BE8002C12517D81";
        internal const string EngineName = "ScoringGUI-1.0.0.exe";
        internal const string AppName = "ScoringGUI";

        internal static string SelfPath { get { return Application.ExecutablePath; } }

        internal static string TmpDir
        {
            get { return Path.Combine(Path.GetTempPath(), "ScoringGuiSetup-" + Environment.UserName); }
        }

        internal static string EnginePath { get { return Path.Combine(TmpDir, EngineName); } }

        internal static string LogPath { get { return Path.Combine(Path.GetTempPath(), "ScoringGUI-install.log"); } }

        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(IsInstalled() ? (Form)new ManageForm() : new InstallWizard());
        }

        internal static bool IsInstalled()
        {
            try
            {
                using (RegistryKey k = Registry.CurrentUser.OpenSubKey(
                    @"Software\Microsoft\Installer\Products\" + Squashed, false))
                {
                    return k != null;
                }
            }
            catch { return false; }
        }

        internal static string InstallLocation()
        {
            string sid = WindowsIdentity.GetCurrent().User.Value;
            try
            {
                object loc = Registry.GetValue(
                    @"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Installer\UserData\"
                    + sid + @"\Products\" + Squashed + @"\InstallProperties",
                    "InstallLocation", null);
                if (loc is string && !string.IsNullOrEmpty((string)loc))
                {
                    return (string)loc;
                }
            }
            catch { }
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), AppName);
        }

        internal static string InstalledExe()
        {
            return Path.Combine(InstallLocation(), AppName + ".exe");
        }

        internal static void LaunchApp()
        {
            try
            {
                Process.Start(new ProcessStartInfo(InstalledExe()) { WorkingDirectory = InstallLocation() });
            }
            catch (Exception e)
            {
                MessageBox.Show("启动程序失败：" + e.Message + "\r\n\r\n程序可能尚未安装完成或已被卸载。",
                    AppName + " 安装程序", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        internal static void StartUninstallWizard()
        {
            try
            {
                Process.Start("msiexec.exe", "/x " + ProductCode);
            }
            catch (Exception e)
            {
                MessageBox.Show("无法启动卸载向导：" + e.Message,
                    AppName + " 安装程序", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        internal static string ExtractEngine()
        {
            Directory.CreateDirectory(TmpDir);
            string dst = EnginePath;
            using (Stream src = Assembly.GetExecutingAssembly()
                       .GetManifestResourceStream("ScoringGuiSetup.payload.bin"))
            {
                if (src == null)
                {
                    throw new InvalidOperationException("安装包内缺少安装引擎，文件可能已损坏。");
                }
                using (FileStream fs = new FileStream(dst, FileMode.Create, FileAccess.Write))
                {
                    src.CopyTo(fs);
                }
            }
            return dst;
        }

        internal static Icon SelfIcon()
        {
            try { return Icon.ExtractAssociatedIcon(SelfPath); }
            catch { return null; }
        }

        internal static void DeleteAfterExit(bool deleteSelf)        {
            try { Directory.Delete(TmpDir, true); } catch { }
            if (!deleteSelf) { return; }
            // 退出后延迟 3 秒删除本安装包文件（cmd 先等待再删，避免文件被占用）
            try
            {
                string args = "/c ping -n 4 127.0.0.1 >nul & del /f /q \"" + SelfPath + "\"";
                Process.Start(new ProcessStartInfo("cmd.exe", args)
                {
                    WindowStyle = ProcessWindowStyle.Hidden,
                    CreateNoWindow = true
                });
            }
            catch { }
        }
    }

    // ================================================================
    // 未安装：安装向导（进度 -> 完成页：勾选删除安装包 / 立即运行）
    // ================================================================
    internal sealed class InstallWizard : Form
    {
        private readonly Label _title;
        private readonly Label _status;
        private readonly ProgressBar _bar;
        private readonly CheckBox _deleteChk;
        private readonly Button _runBtn;
        private readonly Button _finishBtn;
        private bool _busy = true;
        private readonly EventHandler _finishHandler;
        private readonly EventHandler _closeHandler;

        public InstallWizard()
        {
            Text = Program.AppName + " 安装程序";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(460, 210);
            Font = new Font("Microsoft YaHei UI", 9F);
            Icon = Program.SelfIcon();

            _title = new Label
            {
                Text = "学生组织面试评分系统 v1.0.0",
                Font = new Font("Microsoft YaHei UI", 12F, FontStyle.Bold),
                Location = new Point(20, 18),
                AutoSize = true
            };
            _status = new Label
            {
                Text = "正在准备安装…",
                Location = new Point(20, 58),
                Size = new Size(420, 40)
            };
            _bar = new ProgressBar
            {
                Style = ProgressBarStyle.Marquee,
                Location = new Point(20, 104),
                Size = new Size(420, 18)
            };
            _deleteChk = new CheckBox
            {
                Text = "安装完成后删除安装包（ScoringGUI-Setup-1.0.0.exe）",
                Location = new Point(20, 136),
                Size = new Size(420, 20),
                Checked = false,
                Visible = false
            };
            _runBtn = new Button
            {
                Text = "立即运行",
                Location = new Point(300, 166),
                Size = new Size(80, 30),
                Visible = false
            };
            _finishBtn = new Button
            {
                Text = "完成",
                Location = new Point(386, 166),
                Size = new Size(80, 30),
                Visible = false
            };
            _runBtn.Click += (s, e) =>
            {
                Program.LaunchApp();
                FinishAndClose();
            };
            _closeHandler = (s, e) => Close();
            _finishHandler = (s, e) => FinishAndClose();
            _finishBtn.Click += _finishHandler;
            Controls.AddRange(new Control[] { _title, _status, _bar, _deleteChk, _runBtn, _finishBtn });
            Shown += (s, e) => new Thread(DoInstall).Start();
        }

        private void FinishAndClose()
        {
            bool del = _deleteChk.Checked;
            Program.DeleteAfterExit(del);
            Close();
        }

        private void DoInstall()
        {
            SetStatus("正在准备安装…", true);
            try
            {
                Program.ExtractEngine();
            }
            catch (Exception ex)
            {
                ShowFailure("解压安装组件失败：" + ex.Message);
                return;
            }
            SetStatus("正在安装（请稍候，约 1~2 分钟）…", true);
            try
            {
                File.Delete(Program.LogPath);
                Process p = Process.Start(new ProcessStartInfo(Program.EnginePath,
                    "/quiet /norestart /l*v \"" + Program.LogPath + "\""));
                DateTime deadline = DateTime.Now.AddMinutes(5);
                while (!p.WaitForExit(1000))
                {
                    if (DateTime.Now > deadline)
                    {
                        try { p.Kill(); } catch { }
                        ShowFailure("安装程序长时间无响应（可能残留了未完成的旧安装状态）。\r\n\r\n"
                            + "可点击下方【清理残留并重试】自动修复后再次安装；"
                            + "或手动在命令行执行：msiexec /x " + Program.ProductCode + " 后重试。");
                        return;
                    }
                }
                if (p.ExitCode != 0)
                {
                    ShowFailure("安装失败（错误代码 " + p.ExitCode + "）。\r\n\r\n"
                        + "可点击【清理残留并重试】修复旧安装状态后再次安装；\r\n"
                        + "详细日志：" + Program.LogPath);
                    return;
                }
            }
            catch (Exception ex)
            {
                ShowFailure("安装失败：" + ex.Message);
                return;
            }
            ShowDone();
        }

        private void SetStatus(string text, bool marquee)
        {
            if (IsDisposed) { return; }
            BeginInvoke((Action)(() =>
            {
                _status.Text = text;
                _bar.Visible = true;
            }));
        }

        private void ShowDone()
        {
            if (IsDisposed) { return; }
            BeginInvoke((Action)(() =>
            {
                _busy = false;
                _status.Text = "安装完成。已安装到当前用户（无需管理员权限），"
                    + "桌面已创建快捷方式。\r\n卸载请使用：Windows 设置 → 应用 → ScoringGUI → 卸载。";
                _bar.Visible = false;
                _deleteChk.Visible = true;
                _runBtn.Visible = true;
                _finishBtn.Visible = true;
                Text = "安装完成";
            }));
        }

        private void ShowFailure(string message)
        {
            if (IsDisposed) { return; }
            BeginInvoke((Action)(() =>
            {
                _busy = false;
                _status.Text = message;
                _bar.Visible = false;
                Button retry = new Button
                {
                    Text = "清理残留并重试",
                    Location = new Point(200, 166),
                    Size = new Size(120, 30)
                };
                retry.Click += (s, e) =>
                {
                    retry.Enabled = false;
                    _status.Text = "正在清理残留状态…";
                    _bar.Visible = true;
                    new Thread(() =>
                    {
                        try
                        {
                            Process q = Process.Start(new ProcessStartInfo("msiexec.exe",
                                "/x " + Program.ProductCode + " /qn /norestart"));
                            if (q != null) { q.WaitForExit(); }
                        }
                        catch { }
                        BeginInvoke((Action)(() =>
                        {
                            retry.Enabled = false;
                            _status.Text = "正在重新安装…";
                            _bar.Visible = true;
                            new Thread(DoInstall).Start();
                        }));
                    }).Start();
                };
                _finishBtn.Text = "退出";
                _finishBtn.Visible = true;
                _finishBtn.Location = new Point(386, 166);
                _finishBtn.Click -= _finishHandler;
                _finishBtn.Click += _closeHandler;
                Controls.Add(retry);
            }));
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (_busy)
            {
                // 安装进行中不允许直接关窗，防止留下半成品状态
                e.Cancel = true;
                MessageBox.Show("安装正在进行中，请稍候…", Program.AppName + " 安装程序",
                    MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            base.OnFormClosing(e);
        }
    }

    // ================================================================
    // 已安装：管理页（启动 / 卸载 / 关闭）——刻意不提供“重装/修复”，
    // 因为 jpackage 引擎对已安装产品再次安装会静默挂起
    // ================================================================
    internal sealed class ManageForm : Form
    {
        public ManageForm()
        {
            Text = Program.AppName + " 安装程序";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(460, 210);
            Font = new Font("Microsoft YaHei UI", 9F);
            Icon = Program.SelfIcon();

            Label title = new Label
            {
                Text = "ScoringGUI 已安装",
                Font = new Font("Microsoft YaHei UI", 12F, FontStyle.Bold),
                Location = new Point(20, 18),
                AutoSize = true
            };
            Label info = new Label
            {
                Text = "版本：1.0.0\r\n位置：" + Program.InstallLocation() + "\r\n"
                    + "卸载：Windows 设置 → 应用 → ScoringGUI，或点下方【卸载】。",
                Location = new Point(20, 56),
                Size = new Size(420, 64)
            };
            Button runBtn = new Button
            {
                Text = "启动 ScoringGUI",
                Location = new Point(180, 140),
                Size = new Size(130, 30)
            };
            Button unBtn = new Button
            {
                Text = "卸载",
                Location = new Point(316, 140),
                Size = new Size(60, 30)
            };
            Button closeBtn = new Button
            {
                Text = "关闭",
                Location = new Point(382, 140),
                Size = new Size(60, 30)
            };
            runBtn.Click += (s, e) => { Program.LaunchApp(); Close(); };
            unBtn.Click += (s, e) => Program.StartUninstallWizard();
            closeBtn.Click += (s, e) => Close();
            Controls.AddRange(new Control[] { title, info, runBtn, unBtn, closeBtn });
        }
    }
}
