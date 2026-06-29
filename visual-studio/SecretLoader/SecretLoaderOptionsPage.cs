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

using System.ComponentModel;
using Microsoft.VisualStudio.Shell;

namespace SecretLoader
{
    /// <summary>
    /// Tools → Options → SecretLoader → General. Persisted by VS; on load/apply it pushes the values
    /// into <see cref="SecretLoaderSettings.Current"/>, which the launch-targets provider reads.
    /// </summary>
    public sealed class SecretLoaderOptionsPage : DialogPage
    {
        [Category("Vault")]
        [DisplayName("Preset")]
        [Description("Pick a vault to fill in the CLI / command / JSON-path below, then tweak. Resets to Custom after applying.")]
        public VaultPreset Preset { get; set; } = VaultPreset.Custom;

        [Category("Vault")]
        [DisplayName("CLI path")]
        [Description("The vault CLI executable (e.g. infisical, vault, doppler, aws). May be a full path.")]
        public string CliPath { get; set; } = "infisical";

        [Category("Vault")]
        [DisplayName("Command template")]
        [Description("Command that prints secrets as JSON. Placeholders: {cli} {env} {project}.")]
        public string CommandTemplate { get; set; } = "{cli} export --format=json --env={env} --projectId={project}";

        [Category("Vault")]
        [DisplayName("JSON path")]
        [Description("Path to the secrets map in the CLI output. Default $ (root). Example: $.data.data")]
        public string JsonPath { get; set; } = "$";

        [Category("Vault")]
        [DisplayName("Project-list command")]
        [Description("Optional. Command that prints a JSON list of projects for the popup's \"Pick…\" button. Placeholders: {cli} {env}.")]
        public string ListProjectsCommand { get; set; } = "";

        [Category("Vault")]
        [DisplayName("Default environment")]
        [Description("Fallback environment when none is set on the profile / repo config.")]
        public string DefaultEnvironment { get; set; } = "dev";

        [Category("Behavior")]
        [DisplayName("Strict mode")]
        [Description("Block the launch if secrets fail to load. (0 keys returned is still a valid success.)")]
        public bool StrictMode { get; set; } = true;

        [Category("Behavior")]
        [DisplayName("Prefer local variables")]
        [Description("Don't overwrite environment variables already set on the launch profile.")]
        public bool PreferLocalVariables { get; set; } = true;

        [Category("Behavior")]
        [DisplayName("Execution timeout (seconds)")]
        [Description("Force-kill the CLI if it hangs longer than this.")]
        public int TimeoutSeconds { get; set; } = 10;

        [Category("Behavior")]
        [DisplayName("Blacklist")]
        [Description("Key names / globs that are never injected. Separate with commas or new lines.")]
        public string BlacklistRaw { get; set; } = "";

        [Category("Cache")]
        [DisplayName("Enable cache")]
        [Description("Cache fetched secrets in memory between launches.")]
        public bool EnableCache { get; set; } = true;

        [Category("Cache")]
        [DisplayName("Cache duration (minutes)")]
        public int CacheDurationMinutes { get; set; } = 5;

        public override void LoadSettingsFromStorage()
        {
            // Read from the shared WritableSettingsStore (same store the new Settings UI binds to) so the
            // legacy Options dialog and the unified UI always show the same values.
            SecretLoaderSettings s = SettingsStorage.Load();
            CliPath = s.CliPath;
            CommandTemplate = s.CommandTemplate;
            JsonPath = s.JsonPath;
            ListProjectsCommand = s.ListProjectsCommand;
            DefaultEnvironment = s.DefaultEnvironment;
            StrictMode = s.StrictMode;
            PreferLocalVariables = s.PreferLocalVariables;
            TimeoutSeconds = s.TimeoutSeconds;
            BlacklistRaw = s.BlacklistRaw;
            EnableCache = s.EnableCache;
            CacheDurationMinutes = s.CacheDurationMinutes;
            Preset = VaultPreset.Custom;
            SecretLoaderSettings.Current = s;
        }

        protected override void OnApply(PageApplyEventArgs e)
        {
            base.OnApply(e);

            // A preset fills the editable fields, then snaps back to Custom so the user can tweak from there.
            if (Preset != VaultPreset.Custom)
            {
                var (cli, template, jsonPath, list) = VaultPresets.Get(Preset);
                CliPath = cli;
                CommandTemplate = template;
                JsonPath = jsonPath;
                ListProjectsCommand = list;
                Preset = VaultPreset.Custom;
            }

            var settings = new SecretLoaderSettings
            {
                CliPath = CliPath,
                CommandTemplate = CommandTemplate,
                JsonPath = JsonPath,
                ListProjectsCommand = ListProjectsCommand,
                DefaultEnvironment = DefaultEnvironment,
                StrictMode = StrictMode,
                PreferLocalVariables = PreferLocalVariables,
                TimeoutSeconds = TimeoutSeconds,
                BlacklistRaw = BlacklistRaw,
                EnableCache = EnableCache,
                CacheDurationMinutes = CacheDurationMinutes,
            };
            SettingsStorage.Save(settings);
            SecretLoaderSettings.Current = settings;
        }

        public override void SaveSettingsToStorage()
        {
            // Persistence is handled in OnApply via the shared store; suppress the default DialogPage store
            // so we never keep a second, diverging copy of the values.
        }
    }
}
