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
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.ComponentModel.Composition;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.VisualStudio.ProjectSystem;
using Microsoft.VisualStudio.ProjectSystem.Debug;
using Microsoft.VisualStudio.ProjectSystem.VS.Debug;

namespace SecretLoader
{
    /// <summary>
    /// The "chokepoint" launch-targets provider. Every F5 / Ctrl+F5 / menu debug launch of a CPS project
    /// converges here. We register at a HIGHER order than the default provider, claim the same profiles,
    /// and delegate to the real provider (default OR the web tooling) — but we hand it an AUGMENTED
    /// profile: a copy of the active profile with the vault's secrets merged into its environment.
    ///
    /// Why augment the profile we pass DOWN (not the targets returned): web projects launch Kestrel
    /// themselves and return zero targets, reading env from the profile. Passing an augmented profile
    /// injects env for BOTH web and non-web, with no launchSettings.json write and nothing pushed into
    /// the snapshot (so it's not shown in the profile UI). Fresh per launch; fail-closed by throwing.
    /// </summary>
    [Export(typeof(IDebugProfileLaunchTargetsProvider))]
    [AppliesTo("LaunchProfiles")]
    [Order(100000)] // high precedence so CPS calls us before the (low-order) default provider
    internal sealed class SecretLoaderLaunchTargetsProvider : IDebugProfileLaunchTargetsProvider, IDebugProfileLaunchTargetsProvider2
    {
        private readonly UnconfiguredProject _unconfigured;

        // One fetch+augment per original profile, reused across OnBefore/Query/OnAfter of a launch, then
        // cleared in OnAfter so the next launch re-fetches (fresh per launch).
        private readonly ConditionalWeakTable<ILaunchProfile, Task<ILaunchProfile>> _augmented = new();

        // TTL cache of fetched secrets, keyed by projectId|workingDir|env|template (shared across projects).
        private static readonly ConcurrentDictionary<string, CacheEntry> SecretsCache = new();

        private readonly struct CacheEntry
        {
            public CacheEntry(DateTime at, IReadOnlyDictionary<string, string> secrets) { At = at; Secrets = secrets; }
            public DateTime At { get; }
            public IReadOnlyDictionary<string, string> Secrets { get; }
        }

        [ImportingConstructor]
        public SecretLoaderLaunchTargetsProvider(ConfiguredProject configuredProject)
        {
            _unconfigured = configuredProject.UnconfiguredProject;
            LaunchTargetsProviders = new OrderPrecedenceImportCollection<IDebugProfileLaunchTargetsProvider>(
                projectCapabilityCheckProvider: configuredProject.UnconfiguredProject);
        }

        [ImportMany]
        public OrderPrecedenceImportCollection<IDebugProfileLaunchTargetsProvider> LaunchTargetsProviders { get; }

        public bool SupportsProfile(ILaunchProfile profile) => GetDelegate(profile) is not null;

        // Ctrl+F5 / run-without-debugging path.
        public async Task<IReadOnlyList<IDebugLaunchSettings>> QueryDebugTargetsAsync(
            DebugLaunchOptions launchOptions, ILaunchProfile activeProfile)
        {
            IDebugProfileLaunchTargetsProvider? inner = GetDelegate(activeProfile);
            return inner is null
                ? Array.Empty<IDebugLaunchSettings>()
                : await inner.QueryDebugTargetsAsync(launchOptions, await AugmentAsync(activeProfile));
        }

        // F5 / debug path — the orchestrator calls THIS on V2 providers (e.g. the web tooling).
        public async Task<IReadOnlyList<IDebugLaunchSettings>> QueryDebugTargetsForDebugLaunchAsync(
            DebugLaunchOptions launchOptions, ILaunchProfile profile)
        {
            IDebugProfileLaunchTargetsProvider? inner = GetDelegate(profile);
            if (inner is null) return Array.Empty<IDebugLaunchSettings>();

            ILaunchProfile augmented = await AugmentAsync(profile);
            return inner is IDebugProfileLaunchTargetsProvider2 inner2
                ? await inner2.QueryDebugTargetsForDebugLaunchAsync(launchOptions, augmented)
                : await inner.QueryDebugTargetsAsync(launchOptions, augmented);
        }

        public async Task OnBeforeLaunchAsync(DebugLaunchOptions launchOptions, ILaunchProfile profile)
        {
            IDebugProfileLaunchTargetsProvider? inner = GetDelegate(profile);
            if (inner != null) await inner.OnBeforeLaunchAsync(launchOptions, await AugmentAsync(profile));
        }

        public async Task OnAfterLaunchAsync(DebugLaunchOptions launchOptions, ILaunchProfile profile)
        {
            IDebugProfileLaunchTargetsProvider? inner = GetDelegate(profile);
            if (inner != null) await inner.OnAfterLaunchAsync(launchOptions, await AugmentAsync(profile));
            _augmented.Remove(profile); // launch finished — re-fetch next time
        }

        /// <summary>Returns the active profile with vault secrets merged in (cached per launch).</summary>
        private Task<ILaunchProfile> AugmentAsync(ILaunchProfile profile)
        {
            if (_augmented.TryGetValue(profile, out Task<ILaunchProfile> existing) && !existing.IsFaulted)
            {
                return existing;
            }
            _augmented.Remove(profile);
            Task<ILaunchProfile> task = BuildAugmentedAsync(profile);
            _augmented.Add(profile, task);
            return task;
        }

        private async Task<ILaunchProfile> BuildAugmentedAsync(ILaunchProfile profile)
        {
            SecretLoaderSettings settings = SecretLoaderSettings.Current;

            string? basePath = ProjectDirectory();
            if (basePath is null) return profile;

            string? configFile = ProjectConfig.FindConfigFile(basePath);
            string workingDir = (configFile != null ? Path.GetDirectoryName(configFile) : basePath) ?? basePath;

            PerProjectSettings perProject = PerProjectStore.Get(_unconfigured?.FullPath);
            bool strictMode = perProject.StrictMode ?? settings.StrictMode;
            bool preferLocal = perProject.PreferLocalVariables ?? settings.PreferLocalVariables;

            // Environment precedence: profile SECRETLOADER_ENV > per-project > git-branch map > repo default > global.
            string env = ProfileEnv(profile, "SECRETLOADER_ENV")
                         ?? NullIfBlank(perProject.Environment)
                         ?? ProjectConfig.BranchEnvironment(basePath)
                         ?? ProjectConfig.DetectDefaultEnvironment(basePath)
                         ?? settings.DefaultEnvironment;

            // Project id: per-project override > auto-detected from config.
            string projectId = NullIfBlank(perProject.ProjectId) ?? ProjectConfig.DetectProjectId(basePath) ?? "";

            // Project id required by the template, unavailable, and no config file to self-resolve from.
            if (settings.CommandTemplate.Contains("{project}") && projectId.Length == 0 && configFile is null)
            {
                if (strictMode)
                {
                    throw new FetchException("Project id is required but not set, and no .infisical.json/.secretloader.json was found.");
                }
                return profile;
            }

            string cacheKey = $"{projectId}|{workingDir}|{env}|{settings.CommandTemplate}";
            IReadOnlyDictionary<string, string> secrets;
            if (settings.EnableCache
                && SecretsCache.TryGetValue(cacheKey, out CacheEntry hit)
                && (DateTime.UtcNow - hit.At).TotalMinutes < settings.CacheDurationMinutes)
            {
                secrets = hit.Secrets;
            }
            else
            {
                try
                {
                    secrets = await SecretFetcher.FetchAsync(settings, env, projectId, workingDir, CancellationToken.None);
                }
                catch (FetchException) when (!strictMode)
                {
                    return profile; // fail-open: launch without secrets
                }
                // strict mode: let FetchException propagate → aborts the launch (fail-closed)

                if (settings.EnableCache)
                {
                    SecretsCache[cacheKey] = new CacheEntry(DateTime.UtcNow, secrets);
                }
            }

            if (secrets.Count == 0) return profile; // 0 keys is an allowed success

            var existingEnv = profile.EnvironmentVariables;
            var toInject = new Dictionary<string, string>();
            foreach (var kv in secrets)
            {
                if (settings.IsBlacklisted(kv.Key)) continue;
                if (preferLocal && existingEnv != null && existingEnv.ContainsKey(kv.Key)) continue;
                toInject[kv.Key] = kv.Value;
            }

            if (toInject.Count == 0) return profile;

            StatusBarWidget.SetText($"{env} | {toInject.Count} secret(s)");
            return new InMemoryLaunchProfile(profile, toInject);
        }

        private string? ProjectDirectory()
        {
            string? full = _unconfigured?.FullPath;
            return string.IsNullOrEmpty(full) ? null : Path.GetDirectoryName(full);
        }

        private static string? NullIfBlank(string? s) => string.IsNullOrWhiteSpace(s) ? null : s;

        private static string? ProfileEnv(ILaunchProfile profile, string key)
        {
            if (profile.EnvironmentVariables != null
                && profile.EnvironmentVariables.TryGetValue(key, out string? value)
                && !string.IsNullOrEmpty(value))
            {
                return value;
            }
            return null;
        }

        /// <summary>The highest-precedence OTHER provider that handles this profile (web tooling, or the default).</summary>
        private IDebugProfileLaunchTargetsProvider? GetDelegate(ILaunchProfile profile) =>
            LaunchTargetsProviders
                .Select(import => import.Value)
                .FirstOrDefault(p => !ReferenceEquals(p, this) && p.SupportsProfile(profile));
    }
}
