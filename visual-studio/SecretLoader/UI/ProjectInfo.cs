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
using EnvDTE;
using EnvDTE80;
using Microsoft.VisualStudio.Shell;

namespace SecretLoader
{
    /// <summary>Resolves the current startup project and its effective environment (file-based only — no CPS UI calls).</summary>
    internal static class ProjectInfo
    {
        /// <summary>The startup project's file path, else the first project. UI thread only.</summary>
        public static string? ResolveStartupProjectPath()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            return SafeFullName(StartupProject());
        }

        /// <summary>Status-widget suffix: "&lt;project&gt; · &lt;env&gt;".</summary>
        public static string? DescribeSuffix()
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                string? path = ResolveStartupProjectPath();
                if (path is null) return null;
                // Active launch profile's env (tracked off the UI thread) wins; else the effective default.
                string env = ActiveEnvRegistry.Get(path) ?? EffectiveEnvironment(path);
                return $"{Path.GetFileNameWithoutExtension(path)} · {env}";
            }
            catch { return null; }
        }

        /// <summary>Effective default env: per-project override → git-branch map → repo config default → global.</summary>
        public static string EffectiveEnvironment(string projectFullPath)
        {
            string dir = Path.GetDirectoryName(projectFullPath) ?? "";
            PerProjectSettings pp = PerProjectStore.Get(projectFullPath);
            return NotBlank(pp.Environment)
                   ?? ProjectConfig.BranchEnvironment(dir)
                   ?? ProjectConfig.DetectDefaultEnvironment(dir)
                   ?? SecretLoaderSettings.Current.DefaultEnvironment;
        }

        public static string? EffectiveProjectId(string projectFullPath)
        {
            string dir = Path.GetDirectoryName(projectFullPath) ?? "";
            PerProjectSettings pp = PerProjectStore.Get(projectFullPath);
            return NotBlank(pp.ProjectId) ?? ProjectConfig.DetectProjectId(dir);
        }

        private static Project? StartupProject()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (Package.GetGlobalService(typeof(DTE)) is not DTE2 dte || dte.Solution is null) return null;

            try
            {
                if (dte.Solution.SolutionBuild?.StartupProjects is Array arr && arr.Length > 0
                    && arr.GetValue(0) is string uniqueName)
                {
                    foreach (Project proj in dte.Solution.Projects)
                    {
                        try { if (proj.UniqueName == uniqueName && !string.IsNullOrEmpty(proj.FullName)) return proj; }
                        catch { /* solution folders throw */ }
                    }
                }
            }
            catch { /* ignore */ }

            try
            {
                foreach (Project proj in dte.Solution.Projects)
                {
                    try { if (!string.IsNullOrEmpty(proj.FullName)) return proj; }
                    catch { /* ignore */ }
                }
            }
            catch { /* ignore */ }

            return null;
        }

        private static string? SafeFullName(Project? p) { try { return string.IsNullOrEmpty(p?.FullName) ? null : p!.FullName; } catch { return null; } }
        private static string? NotBlank(string? s) => string.IsNullOrWhiteSpace(s) ? null : s;
    }
}
