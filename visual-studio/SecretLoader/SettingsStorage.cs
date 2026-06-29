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

using Microsoft.VisualStudio.Settings;
using Microsoft.VisualStudio.Shell;
using Microsoft.VisualStudio.Shell.Settings;

namespace SecretLoader
{
    /// <summary>
    /// Reads/writes the global settings via the VS <see cref="WritableSettingsStore"/> at collection
    /// "SecretLoader". This is the SAME VsUserSettingsRegistry store that the Unified Settings UI binds to
    /// (see UnifiedSettings/secretloader.registration.json), so the new-UI editor and the legacy Options
    /// page share one source of truth. Property names here MUST match the registration.json migration paths.
    ///
    /// Call on the UI thread (uses <see cref="ServiceProvider.GlobalProvider"/>).
    /// </summary>
    internal static class SettingsStorage
    {
        private const string Collection = "SecretLoader";
        private static WritableSettingsStore? _store;

        private static WritableSettingsStore? Store()
        {
            if (_store != null) return _store;
            try
            {
                var manager = new ShellSettingsManager(ServiceProvider.GlobalProvider);
                _store = manager.GetWritableSettingsStore(SettingsScope.UserSettings);
            }
            catch { _store = null; }
            return _store;
        }

        public static SecretLoaderSettings Load()
        {
            var s = new SecretLoaderSettings();
            try
            {
                WritableSettingsStore? store = Store();
                if (store == null || !store.CollectionExists(Collection)) return s;

                s.CliPath = store.GetString(Collection, "CliPath", s.CliPath);
                s.CommandTemplate = store.GetString(Collection, "CommandTemplate", s.CommandTemplate);
                s.JsonPath = store.GetString(Collection, "JsonPath", s.JsonPath);
                s.ListProjectsCommand = store.GetString(Collection, "ListProjectsCommand", s.ListProjectsCommand);
                s.DefaultEnvironment = store.GetString(Collection, "DefaultEnvironment", s.DefaultEnvironment);
                s.StrictMode = store.GetBoolean(Collection, "StrictMode", s.StrictMode);
                s.PreferLocalVariables = store.GetBoolean(Collection, "PreferLocalVariables", s.PreferLocalVariables);
                s.TimeoutSeconds = store.GetInt32(Collection, "TimeoutSeconds", s.TimeoutSeconds);
                s.BlacklistRaw = store.GetString(Collection, "Blacklist", s.BlacklistRaw);
                s.EnableCache = store.GetBoolean(Collection, "EnableCache", s.EnableCache);
                s.CacheDurationMinutes = store.GetInt32(Collection, "CacheDurationMinutes", s.CacheDurationMinutes);
            }
            catch { /* fall back to defaults */ }
            return s;
        }

        public static void Save(SecretLoaderSettings s)
        {
            try
            {
                WritableSettingsStore? store = Store();
                if (store == null) return;
                if (!store.CollectionExists(Collection)) store.CreateCollection(Collection);

                store.SetString(Collection, "CliPath", s.CliPath ?? "");
                store.SetString(Collection, "CommandTemplate", s.CommandTemplate ?? "");
                store.SetString(Collection, "JsonPath", s.JsonPath ?? "");
                store.SetString(Collection, "ListProjectsCommand", s.ListProjectsCommand ?? "");
                store.SetString(Collection, "DefaultEnvironment", s.DefaultEnvironment ?? "");
                store.SetBoolean(Collection, "StrictMode", s.StrictMode);
                store.SetBoolean(Collection, "PreferLocalVariables", s.PreferLocalVariables);
                store.SetInt32(Collection, "TimeoutSeconds", s.TimeoutSeconds);
                store.SetString(Collection, "Blacklist", s.BlacklistRaw ?? "");
                store.SetBoolean(Collection, "EnableCache", s.EnableCache);
                store.SetInt32(Collection, "CacheDurationMinutes", s.CacheDurationMinutes);
            }
            catch { /* best effort */ }
        }
    }
}
