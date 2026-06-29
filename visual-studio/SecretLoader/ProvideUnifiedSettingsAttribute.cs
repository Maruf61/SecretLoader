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
using Microsoft.VisualStudio.Shell;

namespace SecretLoader
{
    /// <summary>
    /// Emits the pkgdef <c>[$RootKey$\SettingsManifests\{packageGuid}]</c> key that points VS at our
    /// Unified Settings <c>registration.json</c>, so the settings render natively in the VS 2026 Settings
    /// UI. Mirrors how dotnet/razor registers <c>razor.registration.json</c>.
    /// </summary>
    [AttributeUsage(AttributeTargets.Class, AllowMultiple = false, Inherited = false)]
    internal sealed class ProvideUnifiedSettingsAttribute : RegistrationAttribute
    {
        private readonly string _packageGuid;
        private readonly string _packageType;
        private readonly string _relativeManifestPath;
        private readonly string _legacyToolsOptionsPath;

        /// <param name="legacyToolsOptionsPath">"{category}\{page}" of the legacy ProvideOptionPage to mark as
        /// already covered by the manifest (so the new UI drops its duplicate "not migrated" bridge). Optional.</param>
        public ProvideUnifiedSettingsAttribute(string packageGuid, string packageType, string relativeManifestPath, string legacyToolsOptionsPath = "")
        {
            _packageGuid = packageGuid;
            _packageType = packageType;
            _relativeManifestPath = relativeManifestPath;
            _legacyToolsOptionsPath = legacyToolsOptionsPath;
        }

        private string KeyName => $@"SettingsManifests\{{{_packageGuid}}}";

        public override void Register(RegistrationContext context)
        {
            using (Key key = context.CreateKey(KeyName))
            {
                key.SetValue(string.Empty, _packageType);
                key.SetValue("ManifestPath", $@"{context.ComponentPath}\{_relativeManifestPath}");
                // Bump this when registration.json changes so VS refreshes its settings cache.
                key.SetValue("CacheTag", (long)0x01);
            }

            if (!string.IsNullOrEmpty(_legacyToolsOptionsPath))
            {
                // Emitted after the auto-generated ProvideOptionPage key, so this value wins: the new UI then
                // treats the legacy page as represented in Unified Settings and hides the duplicate bridge.
                using (Key legacy = context.CreateKey($@"ToolsOptionsPages\{_legacyToolsOptionsPath}"))
                {
                    legacy.SetValue("IsInUnifiedSettings", 1);
                }
            }
        }

        public override void Unregister(RegistrationContext context) => context.RemoveKey(KeyName);
    }
}
