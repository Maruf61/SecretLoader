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
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.VisualStudio.Shell;

namespace SecretLoader
{
    /// <summary>
    /// A clickable SecretLoader widget injected into the VS status bar (technique from Mads Kristensen's
    /// ShowTheShortcut). VS has no public status-bar widget API, so we add a control directly into the
    /// status bar's WPF visual tree. Clicking it opens the per-project settings popup — the Rider-style
    /// clickable widget, without any .vsct/menu plumbing.
    /// </summary>
    internal static class StatusBarWidget
    {
        private static Border? _item;
        private static TextBlock? _text;
        private static Popup? _popup;
        private static DispatcherTimer? _timer;

        public static async Task InjectAsync()
        {
            try
            {
                await ThreadHelper.JoinableTaskFactory.SwitchToMainThreadAsync();
                if (_item != null) return; // already injected

                Panel? panel = null;
                for (int i = 0; i < 30 && panel == null; i++)
                {
                    panel = FindStatusBarPanel(Application.Current?.MainWindow);
                    if (panel == null) await Task.Delay(500);
                }
                if (panel == null) return;

                _text = new TextBlock
                {
                    Text = "🔒 SecretLoader",
                    VerticalAlignment = VerticalAlignment.Center,
                    Foreground = Brushes.White,
                };
                _item = new Border
                {
                    Child = _text,
                    Padding = new Thickness(8, 0, 8, 0),
                    Background = Brushes.Transparent,
                    Cursor = Cursors.Hand,
                    ToolTip = "SecretLoader — project settings",
                };
                _item.MouseLeftButtonUp += (s, e) => TogglePopup();

                DockPanel.SetDock(_item, Dock.Left);
                panel.Children.Insert(System.Math.Min(1, panel.Children.Count), _item);

                RefreshText();

                // Poll (file-based only) so the text tracks per-project/config changes.
                _timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(2) };
                _timer.Tick += (s, e) => RefreshText();
                _timer.Start();
            }
            catch { /* never let widget injection break package load */ }
        }

        /// <summary>Updates the widget to the current startup project + effective env.</summary>
        public static void RefreshText()
        {
            _ = ThreadHelper.JoinableTaskFactory.RunAsync(async () =>
            {
                try
                {
                    await ThreadHelper.JoinableTaskFactory.SwitchToMainThreadAsync();
                    if (_text == null) return;

                    // Pick up edits made in the new Settings UI / legacy dialog (shared store) without a restart.
                    SecretLoaderSettings.Current = SettingsStorage.Load();

                    string? suffix = ProjectInfo.DescribeSuffix();
                    _text.Text = suffix == null ? "🔒 SecretLoader" : "🔒 " + suffix;
                }
                catch { /* widget text is cosmetic */ }
            });
        }

        /// <summary>Mads's named DockPanel, falling back to the first horizontal panel inside any StatusBar element.</summary>
        private static Panel? FindStatusBarPanel(DependencyObject? root)
        {
            if (FindChild(root, "StatusBarPanel") is Panel named) return named;

            DependencyObject? statusBar = FindByTypeName(root, "StatusBar");
            return statusBar == null ? null : FindFirstPanel(statusBar);
        }

        private static DependencyObject? FindByTypeName(DependencyObject? parent, string typeNameContains)
        {
            if (parent == null) return null;
            int count = VisualTreeHelper.GetChildrenCount(parent);
            for (int i = 0; i < count; i++)
            {
                DependencyObject child = VisualTreeHelper.GetChild(parent, i);
                if (child.GetType().Name.IndexOf(typeNameContains, System.StringComparison.OrdinalIgnoreCase) >= 0)
                    return child;
                DependencyObject? result = FindByTypeName(child, typeNameContains);
                if (result != null) return result;
            }
            return null;
        }

        private static Panel? FindFirstPanel(DependencyObject? parent)
        {
            if (parent == null) return null;
            int count = VisualTreeHelper.GetChildrenCount(parent);
            for (int i = 0; i < count; i++)
            {
                DependencyObject child = VisualTreeHelper.GetChild(parent, i);
                if (child is DockPanel or StackPanel or Grid && child is Panel p && p.IsVisible) return p;
                Panel? result = FindFirstPanel(child);
                if (result != null) return result;
            }
            return null;
        }

        /// <summary>Updates the widget label, e.g. "SecretLoader: dev | 12". Safe to call from any thread.</summary>
        public static void SetText(string text)
        {
            _ = ThreadHelper.JoinableTaskFactory.RunAsync(async () =>
            {
                await ThreadHelper.JoinableTaskFactory.SwitchToMainThreadAsync();
                if (_text != null) _text.Text = "🔒 " + text;
            });
        }

        private static void TogglePopup()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (_popup != null && _popup.IsOpen) { _popup.IsOpen = false; return; }

            _popup = new Popup
            {
                PlacementTarget = _item,
                Placement = PlacementMode.Top,
                StaysOpen = false,
                AllowsTransparency = true,
                Child = new Border
                {
                    Background = SystemColors.WindowBrush,
                    BorderBrush = SystemColors.ActiveBorderBrush,
                    BorderThickness = new Thickness(1),
                    Child = new SecretLoaderToolWindowControl { Width = 320 },
                },
            };
            _popup.IsOpen = true;
        }

        private static FrameworkElement? FindChild(DependencyObject? parent, string name)
        {
            if (parent == null) return null;
            int count = VisualTreeHelper.GetChildrenCount(parent);
            for (int i = 0; i < count; i++)
            {
                DependencyObject child = VisualTreeHelper.GetChild(parent, i);
                if (child is FrameworkElement fe && fe.Name == name) return fe;
                FrameworkElement? result = FindChild(child, name);
                if (result != null) return result;
            }
            return null;
        }
    }
}
