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
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Newtonsoft.Json.Linq;

namespace SecretLoader
{
    /// <summary>
    /// Locates and reads the repo's vault config (.infisical.json / .secretloader.json / doppler.yaml)
    /// and the current git branch. C# port of the JetBrains ProjectConfig. Holds the project id +
    /// optional defaults — never secrets.
    /// </summary>
    internal static class ProjectConfig
    {
        private static readonly string[] ConfigNames = { ".infisical.json", ".secretloader.json", "doppler.yaml", ".doppler.yaml" };

        private static readonly HashSet<string> SkipDirs = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".git", ".idea", ".vs", "node_modules", "bin", "obj", "build", "out", "dist", "target", "packages"
        };

        /// <summary>Nearest config file: base dir, up to 3 parents, then a shallow (≤2) subfolder scan.</summary>
        public static string? FindConfigFile(string basePath)
        {
            if (string.IsNullOrEmpty(basePath)) return null;

            string? inBase = ConfigIn(basePath);
            if (inBase != null) return inBase;

            var parent = Directory.GetParent(basePath);
            int up = 0;
            while (parent != null && up < 3)
            {
                string? hit = ConfigIn(parent.FullName);
                if (hit != null) return hit;
                parent = parent.Parent;
                up++;
            }

            var queue = new Queue<(string dir, int depth)>();
            foreach (var d in ChildDirs(basePath)) queue.Enqueue((d, 1));
            while (queue.Count > 0)
            {
                var (dir, depth) = queue.Dequeue();
                string? hit = ConfigIn(dir);
                if (hit != null) return hit;
                if (depth < 2)
                {
                    foreach (var d in ChildDirs(dir)) queue.Enqueue((d, depth + 1));
                }
            }
            return null;
        }

        /// <summary>Directory of the nearest config file — the right CWD to run the vault CLI from.</summary>
        public static string? FindConfigDir(string basePath)
        {
            string? file = FindConfigFile(basePath);
            return file == null ? null : Path.GetDirectoryName(file);
        }

        /// <summary>Project id from the nearest config: projectId (generic) or workspaceId (Infisical).</summary>
        public static string? DetectProjectId(string basePath)
        {
            JObject? root = ReadRoot(basePath);
            string? id = (string?)(root?["projectId"] ?? root?["workspaceId"]);
            return string.IsNullOrWhiteSpace(id) ? null : id;
        }

        /// <summary>Repo default environment from the nearest config (defaultEnvironment), if present.</summary>
        public static string? DetectDefaultEnvironment(string basePath)
        {
            string? env = (string?)ReadRoot(basePath)?["defaultEnvironment"];
            return string.IsNullOrWhiteSpace(env) ? null : env;
        }

        /// <summary>Maps the current git branch → environment via gitBranchToEnvironmentMapping.</summary>
        public static string? BranchEnvironment(string basePath)
        {
            string? file = FindConfigFile(basePath);
            if (file == null) return null;
            JObject? root = TryReadJson(file);
            if (root?["gitBranchToEnvironmentMapping"] is not JObject mapping) return null;

            string? branch = CurrentGitBranch(Path.GetDirectoryName(file) ?? basePath);
            if (branch == null) return null;

            string? env = (string?)mapping[branch];
            return string.IsNullOrWhiteSpace(env) ? null : env;
        }

        private static string? ConfigIn(string dir)
        {
            foreach (var name in ConfigNames)
            {
                string path = Path.Combine(dir, name);
                if (File.Exists(path)) return path;
            }
            return null;
        }

        private static IEnumerable<string> ChildDirs(string dir)
        {
            string[] subs;
            try { subs = Directory.GetDirectories(dir); }
            catch { return Enumerable.Empty<string>(); }
            return subs.Where(d =>
            {
                string name = Path.GetFileName(d);
                return !SkipDirs.Contains(name) && !name.StartsWith(".");
            });
        }

        private static JObject? ReadRoot(string basePath)
        {
            string? file = FindConfigFile(basePath);
            return file == null ? null : TryReadJson(file);
        }

        private static JObject? TryReadJson(string file)
        {
            try
            {
                if (!file.EndsWith(".json", StringComparison.OrdinalIgnoreCase)) return null;
                return JObject.Parse(File.ReadAllText(file));
            }
            catch { return null; }
        }

        /// <summary>Reads the current branch from .git/HEAD, walking up from startPath to find the repo.</summary>
        private static string? CurrentGitBranch(string startPath)
        {
            try
            {
                var dir = new DirectoryInfo(startPath);
                int up = 0;
                while (dir != null && up < 6)
                {
                    string head = Path.Combine(dir.FullName, ".git", "HEAD");
                    if (File.Exists(head))
                    {
                        string line = File.ReadAllText(head).Trim();
                        return line.StartsWith("ref:") ? line.Substring(line.LastIndexOf('/') + 1) : null;
                    }
                    dir = dir.Parent;
                    up++;
                }
            }
            catch { /* ignore */ }
            return null;
        }
    }
}
