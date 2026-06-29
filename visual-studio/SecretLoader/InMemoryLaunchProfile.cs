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

using System.Collections.Generic;
using System.Collections.Immutable;
using Microsoft.VisualStudio.ProjectSystem.Debug;

namespace SecretLoader
{
    /// <summary>
    /// An in-memory copy of an existing launch profile with extra environment variables merged in.
    /// Implements <see cref="IPersistOption"/> with <c>DoNotPersist = true</c>, so pushing it through
    /// <c>ILaunchSettingsProvider.AddOrUpdateProfileAsync</c> updates the active launch in memory only —
    /// it is never written to launchSettings.json.
    /// </summary>
    internal sealed class InMemoryLaunchProfile : ILaunchProfile, IPersistOption
    {
        public InMemoryLaunchProfile(ILaunchProfile baseProfile, IReadOnlyDictionary<string, string> extraEnv)
        {
            Name = baseProfile.Name;
            CommandName = baseProfile.CommandName;
            ExecutablePath = baseProfile.ExecutablePath;
            CommandLineArgs = baseProfile.CommandLineArgs;
            WorkingDirectory = baseProfile.WorkingDirectory;
            LaunchBrowser = baseProfile.LaunchBrowser;
            LaunchUrl = baseProfile.LaunchUrl;
            OtherSettings = baseProfile.OtherSettings;

            ImmutableDictionary<string, string> env = baseProfile.EnvironmentVariables ?? ImmutableDictionary<string, string>.Empty;
            foreach (KeyValuePair<string, string> kv in extraEnv)
            {
                env = env.SetItem(kv.Key, kv.Value);
            }
            EnvironmentVariables = env;
        }

        public string? Name { get; }
        public string? CommandName { get; }
        public string? ExecutablePath { get; }
        public string? CommandLineArgs { get; }
        public string? WorkingDirectory { get; }
        public bool LaunchBrowser { get; }
        public string? LaunchUrl { get; }
        public ImmutableDictionary<string, string>? EnvironmentVariables { get; }
        public ImmutableDictionary<string, object>? OtherSettings { get; }

        // Keep this profile in memory only — do not write it to launchSettings.json.
        public bool DoNotPersist => true;
    }
}
