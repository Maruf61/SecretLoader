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
using System.ComponentModel.Composition;
using System.Threading.Tasks;
using System.Threading.Tasks.Dataflow;
using Microsoft.VisualStudio.ProjectSystem;
using Microsoft.VisualStudio.ProjectSystem.Debug;

namespace SecretLoader
{
    /// <summary>
    /// Project-scoped, READ-ONLY tracker: subscribes to launch-settings changes (on CPS threads) and
    /// records the active launch profile's SECRETLOADER_ENV into <see cref="ActiveEnvRegistry"/> so the
    /// status widget can show it without touching CPS on the UI thread. Never writes launchSettings.json.
    /// </summary>
    [Export(ExportContractNames.Scopes.UnconfiguredProject, typeof(IProjectDynamicLoadComponent))]
    [AppliesTo("LaunchProfiles")]
    internal sealed class LaunchEnvTracker : IProjectDynamicLoadComponent
    {
        private readonly UnconfiguredProject _project;
        private readonly ILaunchSettingsProvider _launchSettings;
        private IDisposable? _subscription;

        [ImportingConstructor]
        public LaunchEnvTracker(UnconfiguredProject project, ILaunchSettingsProvider launchSettings)
        {
            _project = project;
            _launchSettings = launchSettings;
        }

        public Task LoadAsync()
        {
            Update(_launchSettings.CurrentSnapshot);

            if (_launchSettings is IProjectValueDataSource<ILaunchSettings> source)
            {
                var block = new ActionBlock<IProjectVersionedValue<ILaunchSettings>>(u => Update(u.Value));
                _subscription = source.SourceBlock.LinkTo(block, new DataflowLinkOptions { PropagateCompletion = true });
            }
            return Task.CompletedTask;
        }

        private void Update(ILaunchSettings? settings)
        {
            ILaunchProfile? active = settings?.ActiveProfile;
            string? env = null;
            if (active?.EnvironmentVariables != null
                && active.EnvironmentVariables.TryGetValue("SECRETLOADER_ENV", out string? value)
                && !string.IsNullOrEmpty(value))
            {
                env = value;
            }
            ActiveEnvRegistry.Set(_project.FullPath, env);
        }

        public Task UnloadAsync()
        {
            _subscription?.Dispose();
            _subscription = null;
            return Task.CompletedTask;
        }
    }
}
