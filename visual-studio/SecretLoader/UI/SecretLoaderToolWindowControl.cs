/*
 * SecretLoader
 * Copyright (C) 2026 Kivi A.Ş.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using Microsoft.VisualStudio.Shell;

namespace SecretLoader
{
    /// <summary>
    /// The per-project settings editor hosted in the tool window — the VS equivalent of the Rider
    /// status-bar popup. Shows the active project + its per-project settings (env, projectId, strict /
    /// prefer-local overrides), with Auto-Detect / Save / Global Settings. Stored per-user in .vs via
    /// <see cref="PerProjectStore"/>.
    /// </summary>
    public sealed class SecretLoaderToolWindowControl : UserControl
    {
        private readonly TextBlock _status = new TextBlock { TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 8) };
        private readonly TextBox _env = new TextBox();
        private readonly TextBox _projectId = new TextBox();
        private readonly CheckBox _strict = new CheckBox { Content = "Strict mode (unchecked = inherit global)", IsThreeState = true, Margin = new Thickness(0, 6, 0, 0) };
        private readonly CheckBox _preferLocal = new CheckBox { Content = "Prefer local variables (unchecked = inherit global)", IsThreeState = true, Margin = new Thickness(0, 4, 0, 0) };

        private string? _projectPath;

        public SecretLoaderToolWindowControl()
        {
            var panel = new StackPanel { Margin = new Thickness(10) };
            panel.Children.Add(new TextBlock { Text = "SecretLoader — Project Settings", FontWeight = FontWeights.Bold, Margin = new Thickness(0, 0, 0, 8) });
            panel.Children.Add(_status);

            panel.Children.Add(Label("Project environment (empty = config / global default):"));
            panel.Children.Add(_env);

            panel.Children.Add(Label("Project ID (empty = auto-detect from .infisical.json):"));
            var idRow = new DockPanel { Margin = new Thickness(0, 2, 0, 0) };
            var auto = new Button { Content = "Auto-Detect", Margin = new Thickness(6, 0, 0, 0), Padding = new Thickness(8, 2, 8, 2) };
            auto.Click += (s, e) => AutoDetect();
            var pick = new Button { Content = "Pick…", Margin = new Thickness(6, 0, 0, 0), Padding = new Thickness(8, 2, 8, 2) };
            pick.Click += (s, e) => PickProjects(pick);
            DockPanel.SetDock(auto, Dock.Right);
            DockPanel.SetDock(pick, Dock.Right);
            idRow.Children.Add(auto);
            idRow.Children.Add(pick);
            idRow.Children.Add(_projectId);
            panel.Children.Add(idRow);

            panel.Children.Add(_strict);
            panel.Children.Add(_preferLocal);

            var buttons = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 12, 0, 0) };
            buttons.Children.Add(MakeButton("Save", (s, e) => Save()));
            buttons.Children.Add(MakeButton("Refresh", (s, e) => Load()));
            buttons.Children.Add(MakeButton("Global Settings…", (s, e) => SecretLoaderPackage.Instance?.OpenOptions()));
            panel.Children.Add(buttons);

            Content = new ScrollViewer { Content = panel, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            Loaded += (s, e) => Load();
        }

        private static TextBlock Label(string text) => new TextBlock { Text = text, Margin = new Thickness(0, 8, 0, 2) };

        private static Button MakeButton(string text, RoutedEventHandler onClick)
        {
            var b = new Button { Content = text, Margin = new Thickness(0, 0, 8, 0), Padding = new Thickness(10, 3, 10, 3) };
            b.Click += onClick;
            return b;
        }

        private void Load()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            _projectPath = ProjectInfo.ResolveStartupProjectPath();
            if (_projectPath is null)
            {
                _status.Text = "No project found. Open/select a project, then Refresh.";
                return;
            }

            PerProjectSettings pp = PerProjectStore.Get(_projectPath);
            _env.Text = pp.Environment ?? "";
            _projectId.Text = pp.ProjectId ?? "";
            _strict.IsChecked = pp.StrictMode;
            _preferLocal.IsChecked = pp.PreferLocalVariables;

            string? dir = Path.GetDirectoryName(_projectPath);
            string? activeEnv = ActiveEnvRegistry.Get(_projectPath);
            string env = activeEnv ?? ProjectInfo.EffectiveEnvironment(_projectPath);
            string envNote = activeEnv != null ? "from active launch profile" : "default (a profile's SECRETLOADER_ENV overrides)";
            string projId = NotBlank(pp.ProjectId) ?? ProjectConfig.DetectProjectId(dir ?? "") ?? "(none)";
            _status.Text = $"Project: {Path.GetFileNameWithoutExtension(_projectPath)}\nEnvironment: {env} ({envNote})\nProject ID: {projId}";
        }

        private void Save()
        {
            if (_projectPath is null) return;
            PerProjectStore.Save(_projectPath, new PerProjectSettings
            {
                Environment = NotBlank(_env.Text),
                ProjectId = NotBlank(_projectId.Text),
                StrictMode = _strict.IsChecked,
                PreferLocalVariables = _preferLocal.IsChecked,
            });
            Load();
            StatusBarWidget.RefreshText();
            _status.Text += "\nSaved.";
        }

        private void AutoDetect()
        {
            if (_projectPath is null) return;
            string? dir = Path.GetDirectoryName(_projectPath);
            string? id = ProjectConfig.DetectProjectId(dir ?? "");
            if (!string.IsNullOrEmpty(id)) _projectId.Text = id;
            if (string.IsNullOrWhiteSpace(_env.Text))
            {
                string? e = ProjectConfig.DetectDefaultEnvironment(dir ?? "");
                if (e != null) _env.Text = e;
            }
            _status.Text = id != null ? "Auto-detected project id from config." : "No .infisical.json / .secretloader.json found nearby.";
        }

        /// <summary>Opens a menu to set the project id — choose from the list command and/or run `infisical init`.
        /// (Manual entry is always available via the text box above.)</summary>
        private void PickProjects(Button anchor)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (_projectPath is null) return;

            SecretLoaderSettings settings = SecretLoaderSettings.Current;
            string cli = settings.CliPath ?? "";
            bool isInfisical = Path.GetFileNameWithoutExtension(cli).ToLowerInvariant() == "infisical";
            bool hasList = !string.IsNullOrWhiteSpace(settings.ListProjectsCommand);

            var chooser = new ContextMenu { PlacementTarget = anchor, Placement = PlacementMode.Bottom };
            if (hasList)
            {
                var mi = new MenuItem { Header = "Choose from project list…" };
                mi.Click += (s, e) => LoadProjectList(anchor);
                chooser.Items.Add(mi);
            }
            if (isInfisical)
            {
                var mi = new MenuItem { Header = "Run \"infisical init\"…" };
                mi.Click += (s, e) => RunInfisicalInit(cli);
                chooser.Items.Add(mi);
            }
            if (chooser.Items.Count == 0)
            {
                _status.Text = "Type the project id above, or set a project-list command in Global Settings….";
                return;
            }
            chooser.IsOpen = true;
        }

        /// <summary>Runs the configured project-list command and drops the results into a dropdown to choose from.</summary>
        private void LoadProjectList(Button anchor)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (_projectPath is null) return;

            string projectDir = Path.GetDirectoryName(_projectPath) ?? "";
            string workingDir = ProjectConfig.FindConfigDir(projectDir) ?? projectDir;
            string env = ActiveEnvRegistry.Get(_projectPath) ?? ProjectInfo.EffectiveEnvironment(_projectPath);
            _status.Text = "Loading projects…";

            _ = ThreadHelper.JoinableTaskFactory.RunAsync(async () =>
            {
                try
                {
                    var entries = await SecretFetcher.ListProjectsAsync(
                        SecretLoaderSettings.Current, env, workingDir, CancellationToken.None);

                    await ThreadHelper.JoinableTaskFactory.SwitchToMainThreadAsync();
                    if (entries.Count == 0) { _status.Text = "Project list returned nothing."; return; }

                    var menu = new ContextMenu { PlacementTarget = anchor, Placement = PlacementMode.Bottom };
                    foreach (var entry in entries)
                    {
                        ProjectEntry captured = entry;
                        var mi = new MenuItem { Header = $"{captured.Label}   ({captured.Id})" };
                        mi.Click += (s, e) =>
                        {
                            _projectId.Text = captured.Id;
                            _status.Text = $"Selected: {captured.Label}";
                        };
                        menu.Items.Add(mi);
                    }
                    menu.IsOpen = true;
                }
                catch (Exception ex)
                {
                    await ThreadHelper.JoinableTaskFactory.SwitchToMainThreadAsync();
                    _status.Text = "Project list failed: " + ex.Message;
                }
            });
        }

        /// <summary>Launches `infisical init` in a console so the user can pick a project; it writes .infisical.json.</summary>
        private void RunInfisicalInit(string cli)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            string dir = Path.GetDirectoryName(_projectPath) ?? "";
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = $"/k \"{cli}\" init",
                    WorkingDirectory = dir,
                    UseShellExecute = true,
                });
                _status.Text = "Running 'infisical init' in a console — pick your project, then click Auto-Detect.";
            }
            catch (Exception ex)
            {
                _status.Text = "Couldn't start infisical init: " + ex.Message;
            }
        }

        private static string? NotBlank(string? s) => string.IsNullOrWhiteSpace(s) ? null : s!.Trim();
    }
}
