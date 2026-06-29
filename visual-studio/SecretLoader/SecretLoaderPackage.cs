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
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.VisualStudio;
using Microsoft.VisualStudio.Shell;
using Microsoft.VisualStudio.Shell.Interop;
using Task = System.Threading.Tasks.Task;

namespace SecretLoader
{
    /// <summary>
    /// The package: registers the Options page, applies settings, and injects the clickable status-bar
    /// widget. Auto-loads on solution open.
    /// </summary>
    [PackageRegistration(UseManagedResourcesOnly = true, AllowsBackgroundLoading = true)]
    [ProvideOptionPage(typeof(SecretLoaderOptionsPage), "SecretLoader", "General", 0, 0, supportsAutomation: true)]
    [ProvideUnifiedSettings(PackageGuidString, "SecretLoader.SecretLoaderPackage", @"UnifiedSettings\secretloader.registration.json", @"SecretLoader\General")]
    [ProvideAutoLoad(VSConstants.UICONTEXT.ShellInitialized_string, PackageAutoLoadFlags.BackgroundLoad)]
    [ProvideAutoLoad(VSConstants.UICONTEXT.SolutionExists_string, PackageAutoLoadFlags.BackgroundLoad)]
    [Guid(PackageGuidString)]
    public sealed class SecretLoaderPackage : AsyncPackage
    {
        public const string PackageGuidString = "b4244e4f-49eb-4aad-b203-507308eadc3b";

        public static SecretLoaderPackage? Instance { get; private set; }

        protected override async Task InitializeAsync(CancellationToken cancellationToken, IProgress<ServiceProgressData> progress)
        {
            await this.JoinableTaskFactory.SwitchToMainThreadAsync(cancellationToken);
            Instance = this;

            // Load persisted settings from the shared store (the one the Unified Settings UI also binds to)
            // into SecretLoaderSettings.Current, which the launch-targets provider reads.
            SecretLoaderSettings.Current = SettingsStorage.Load();
            GetDialogPage(typeof(SecretLoaderOptionsPage));

            // Inject the clickable status-bar widget (opens the per-project settings popup).
            await StatusBarWidget.InjectAsync();

            // Keep the widget text current when the startup project changes.
            if (await GetServiceAsync(typeof(SVsShellMonitorSelection)) is IVsMonitorSelection selection)
            {
                selection.AdviseSelectionEvents(new SelectionWatcher(StatusBarWidget.RefreshText), out _);
            }
        }

        /// <summary>Opens Tools → Options → SecretLoader (called from the settings popup).</summary>
        public void OpenOptions() => ShowOptionPage(typeof(SecretLoaderOptionsPage));
    }
}
