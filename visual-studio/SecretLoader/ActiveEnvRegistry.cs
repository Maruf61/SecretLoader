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

namespace SecretLoader
{
    /// <summary>
    /// Thread-safe map of project file path → the active launch profile's SECRETLOADER_ENV. Written by
    /// <see cref="LaunchEnvTracker"/> (on CPS threads, when the profile changes) and read by the status
    /// widget (a cheap dictionary lookup — no CPS access on the UI thread).
    /// </summary>
    internal static class ActiveEnvRegistry
    {
        private static readonly ConcurrentDictionary<string, string> Map = new(StringComparer.OrdinalIgnoreCase);

        public static void Set(string? projectFullPath, string? env)
        {
            if (string.IsNullOrEmpty(projectFullPath)) return;
            if (string.IsNullOrEmpty(env)) Map.TryRemove(projectFullPath!, out _);
            else Map[projectFullPath!] = env!;
        }

        public static string? Get(string? projectFullPath) =>
            !string.IsNullOrEmpty(projectFullPath) && Map.TryGetValue(projectFullPath!, out string env) ? env : null;
    }
}
