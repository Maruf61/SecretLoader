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
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;

namespace SecretLoader
{
    /// <summary>
    /// Global settings (mirrors the JetBrains plugin). <see cref="SecretLoaderOptionsPage"/> populates
    /// <see cref="Current"/> from Tools → Options; the launch-targets provider reads it at launch.
    /// </summary>
    internal sealed class SecretLoaderSettings
    {
        public string CliPath { get; set; } = "infisical";
        public string CommandTemplate { get; set; } = "{cli} export --format=json --env={env} --projectId={project}";
        public string JsonPath { get; set; } = "$";

        /// <summary>Optional command that prints a JSON list of projects, for the "Pick…" picker. Placeholders: {cli} {env}.</summary>
        public string ListProjectsCommand { get; set; } = "";
        public string DefaultEnvironment { get; set; } = "dev";
        public int TimeoutSeconds { get; set; } = 10;

        public bool EnableCache { get; set; } = true;
        public int CacheDurationMinutes { get; set; } = 5;

        /// <summary>Fail-closed: block the launch if secrets fail to load. (0 keys is still a valid success.)</summary>
        public bool StrictMode { get; set; } = true;

        /// <summary>Don't overwrite env vars already set on the launch profile.</summary>
        public bool PreferLocalVariables { get; set; } = true;

        /// <summary>Newline/comma-separated key names or globs that are never injected.</summary>
        public string BlacklistRaw { get; set; } = "";

        public static SecretLoaderSettings Current { get; set; } = new SecretLoaderSettings();

        /// <summary>Parsed blacklist patterns (case-insensitive), supporting `*` / `?` globs.</summary>
        public IReadOnlyList<Regex> BlacklistPatterns()
        {
            var result = new List<Regex>();
            foreach (var raw in BlacklistRaw.Split('\n', ','))
            {
                var glob = raw.Trim();
                if (glob.Length == 0) continue;
                var sb = new StringBuilder("^");
                foreach (var c in glob)
                {
                    if (c == '*') sb.Append(".*");
                    else if (c == '?') sb.Append('.');
                    else if (char.IsLetterOrDigit(c) || c == '_') sb.Append(c);
                    else sb.Append('\\').Append(c);
                }
                sb.Append('$');
                result.Add(new Regex(sb.ToString(), RegexOptions.IgnoreCase));
            }
            return result;
        }

        public bool IsBlacklisted(string key)
        {
            foreach (var p in BlacklistPatterns())
            {
                if (p.IsMatch(key)) return true;
            }
            return false;
        }
    }
}
