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
using System.IO;
using Newtonsoft.Json.Linq;

namespace SecretLoader
{
    /// <summary>Per-project settings — overrides global where set. Env/projectId fall back to config/global.</summary>
    internal sealed class PerProjectSettings
    {
        public string? Environment { get; set; }
        public string? ProjectId { get; set; }
        public bool? StrictMode { get; set; }
        public bool? PreferLocalVariables { get; set; }
    }

    /// <summary>
    /// Stores per-project settings PER-USER in the solution's <c>.vs</c> folder (git-ignored), at
    /// <c>.vs/SecretLoader/projects.json</c> keyed by project file path. Read by the launch provider,
    /// written by the tool window.
    /// </summary>
    internal static class PerProjectStore
    {
        public static PerProjectSettings Get(string? projectFullPath)
        {
            try
            {
                string? file = FileFor(projectFullPath, create: false);
                if (file == null || !File.Exists(file)) return new PerProjectSettings();

                var root = JObject.Parse(File.ReadAllText(file));
                if (root[Key(projectFullPath!)] is not JObject node) return new PerProjectSettings();

                return new PerProjectSettings
                {
                    Environment = (string?)node["environment"],
                    ProjectId = (string?)node["projectId"],
                    StrictMode = (bool?)node["strictMode"],
                    PreferLocalVariables = (bool?)node["preferLocal"],
                };
            }
            catch { return new PerProjectSettings(); }
        }

        public static void Save(string? projectFullPath, PerProjectSettings s)
        {
            try
            {
                string? file = FileFor(projectFullPath, create: true);
                if (file == null) return;

                JObject root = File.Exists(file) ? JObject.Parse(File.ReadAllText(file)) : new JObject();
                var node = new JObject();
                if (!string.IsNullOrWhiteSpace(s.Environment)) node["environment"] = s.Environment;
                if (!string.IsNullOrWhiteSpace(s.ProjectId)) node["projectId"] = s.ProjectId;
                if (s.StrictMode.HasValue) node["strictMode"] = s.StrictMode.Value;
                if (s.PreferLocalVariables.HasValue) node["preferLocal"] = s.PreferLocalVariables.Value;

                root[Key(projectFullPath!)] = node;
                File.WriteAllText(file, root.ToString());
            }
            catch { /* best effort */ }
        }

        private static string Key(string projectFullPath) => projectFullPath.ToLowerInvariant();

        private static string? FileFor(string? projectFullPath, bool create)
        {
            if (string.IsNullOrEmpty(projectFullPath)) return null;
            string? projectDir = Path.GetDirectoryName(projectFullPath);
            if (projectDir == null) return null;

            string? vs = FindVsDir(projectDir);
            if (vs == null)
            {
                if (!create) return null;
                vs = Path.Combine(projectDir, ".vs"); // fallback if no solution .vs found
            }

            string folder = Path.Combine(vs, "SecretLoader");
            if (create) Directory.CreateDirectory(folder);
            return Path.Combine(folder, "projects.json");
        }

        /// <summary>Walks up from <paramref name="startDir"/> to find the solution's existing .vs folder.</summary>
        private static string? FindVsDir(string startDir)
        {
            var dir = new DirectoryInfo(startDir);
            int up = 0;
            while (dir != null && up < 12)
            {
                string vs = Path.Combine(dir.FullName, ".vs");
                if (Directory.Exists(vs)) return vs;
                dir = dir.Parent;
                up++;
            }
            return null;
        }
    }
}
