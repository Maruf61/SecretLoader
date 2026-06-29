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
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace SecretLoader
{
    internal sealed class FetchException : Exception
    {
        public FetchException(string message) : base(message) { }
    }

    /// <summary>A project returned by the project-list command: an id to inject and a human label to show.</summary>
    internal sealed class ProjectEntry
    {
        public string Id { get; }
        public string Label { get; }
        public ProjectEntry(string id, string label) { Id = id; Label = label; }
    }

    /// <summary>
    /// Runs the configured vault CLI and parses its JSON output into key/value secrets. C# port of the
    /// JetBrains SecretFetcher: quote-aware tokenization, argument-injection validation, drop {project}
    /// when blank (CLI self-resolves), timeout, and jsonPath extraction.
    /// </summary>
    internal static class SecretFetcher
    {
        // Reject whitespace and anything that could inject extra CLI arguments.
        private static readonly Regex SafeArg = new Regex("^[A-Za-z0-9_.:/@-]+$", RegexOptions.Compiled);

        public static async Task<IReadOnlyDictionary<string, string>> FetchAsync(
            SecretLoaderSettings settings, string env, string projectId, string workingDir, CancellationToken ct)
        {
            List<string> command = BuildCommand(settings.CliPath, settings.CommandTemplate, env, projectId);
            if (command.Count == 0) throw new FetchException("Command is empty after substitution.");

            string stdout = await RunAsync(command, settings.CliPath, workingDir, settings.TimeoutSeconds, ct).ConfigureAwait(false);
            return Parse(stdout, settings.JsonPath);
        }

        /// <summary>Runs the configured project-list command and parses its JSON into id/label entries (for the picker).</summary>
        public static async Task<IReadOnlyList<ProjectEntry>> ListProjectsAsync(
            SecretLoaderSettings settings, string env, string workingDir, CancellationToken ct)
        {
            List<string> command = BuildListCommand(settings.CliPath, settings.ListProjectsCommand, env);
            if (command.Count == 0) throw new FetchException("No project-list command is configured.");

            string stdout = await RunAsync(command, settings.CliPath, workingDir, settings.TimeoutSeconds, ct).ConfigureAwait(false);
            return ParseProjects(stdout);
        }

        /// <summary>Starts the CLI, enforces the timeout, checks the exit code, and returns stdout.</summary>
        private static async Task<string> RunAsync(List<string> command, string cliPath, string workingDir, int timeoutSeconds, CancellationToken ct)
        {
            var psi = new ProcessStartInfo
            {
                FileName = command[0],
                Arguments = string.Join(" ", command.Skip(1).Select(QuoteArg)),
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                WorkingDirectory = workingDir,
            };

            Process process;
            try
            {
                process = Process.Start(psi) ?? throw new FetchException("Failed to start CLI process.");
            }
            catch (Exception e) when (e is not FetchException)
            {
                throw new FetchException($"Could not start '{cliPath}': {e.Message}");
            }

            using (process)
            {
                Task<string> outTask = process.StandardOutput.ReadToEndAsync();
                Task<string> errTask = process.StandardError.ReadToEndAsync();
                Task exitTask = Task.Run(() => process.WaitForExit());

                Task finished = await Task.WhenAny(exitTask, Task.Delay(timeoutSeconds * 1000, ct)).ConfigureAwait(false);
                if (finished != exitTask)
                {
                    try { process.Kill(); } catch { /* ignore */ }
                    throw new FetchException($"CLI timed out after {timeoutSeconds}s.");
                }

                string stdout = await outTask.ConfigureAwait(false);
                string stderr = await errTask.ConfigureAwait(false);

                if (process.ExitCode != 0)
                {
                    string err = stderr.Trim();
                    if (err.Length > 500) err = err.Substring(0, 500);
                    throw new FetchException($"CLI exited {process.ExitCode}: {err}");
                }
                return stdout;
            }
        }

        /// <summary>Tokenizes the project-list template, substituting {cli} and {env} (no {project}).</summary>
        public static List<string> BuildListCommand(string cliPath, string template, string env)
        {
            if (string.IsNullOrWhiteSpace(template)) return new List<string>();
            if (env.Length > 0 && !SafeArg.IsMatch(env))
                throw new FetchException($"Unsafe environment name '{env}' (only [A-Za-z0-9_.:/@-] allowed).");

            var outList = new List<string>();
            foreach (var token in Tokenize(template))
            {
                outList.Add(token == "{cli}" ? cliPath : token.Replace("{env}", env));
            }
            return outList.Where(t => !string.IsNullOrWhiteSpace(t)).ToList();
        }

        /// <summary>Parses a project-list JSON: array of strings, or array of objects (id/name/slug…), under root or .projects/.workspaces/.data.</summary>
        public static List<ProjectEntry> ParseProjects(string output)
        {
            var list = new List<ProjectEntry>();
            if (string.IsNullOrWhiteSpace(output)) return list;

            JToken node;
            try { node = JToken.Parse(output); }
            catch (Exception e) { throw new FetchException($"Failed to parse project-list JSON: {e.Message}"); }

            JArray? arr = node as JArray
                          ?? node["projects"] as JArray
                          ?? node["workspaces"] as JArray
                          ?? node["data"] as JArray;
            if (arr == null) return list;

            foreach (var item in arr)
            {
                string? id, label;
                if (item is JValue)
                {
                    id = label = (string?)item;
                }
                else
                {
                    id = (string?)(item["id"] ?? item["projectId"] ?? item["workspaceId"] ?? item["slug"] ?? item["_id"]);
                    label = (string?)(item["name"] ?? item["projectName"] ?? item["slug"]) ?? id;
                }
                if (!string.IsNullOrEmpty(id)) list.Add(new ProjectEntry(id!, label ?? id!));
            }
            return list;
        }

        public static List<string> BuildCommand(string cliPath, string template, string env, string projectId)
        {
            if (env.Length > 0 && !SafeArg.IsMatch(env))
                throw new FetchException($"Unsafe environment name '{env}' (only [A-Za-z0-9_.:/@-] allowed).");
            if (projectId.Length > 0 && !SafeArg.IsMatch(projectId))
                throw new FetchException("Unsafe project id (only [A-Za-z0-9_.:/@-] allowed).");

            bool dropProject = string.IsNullOrEmpty(projectId);
            var outList = new List<string>();
            foreach (var token in Tokenize(template))
            {
                if (token == "{cli}")
                {
                    outList.Add(cliPath);
                }
                else if (token.Contains("{project}") && dropProject)
                {
                    // Drop the placeholder; if it's a bare token preceded by a flag, drop the flag too.
                    if (token == "{project}" && outList.Count > 0
                        && outList[outList.Count - 1].StartsWith("-") && !outList[outList.Count - 1].Contains("="))
                    {
                        outList.RemoveAt(outList.Count - 1);
                    }
                }
                else
                {
                    outList.Add(token.Replace("{env}", env).Replace("{project}", projectId));
                }
            }
            return outList.Where(t => !string.IsNullOrWhiteSpace(t)).ToList();
        }

        /// <summary>Quote-aware tokenizer honoring single and double quotes.</summary>
        public static List<string> Tokenize(string input)
        {
            var tokens = new List<string>();
            var sb = new StringBuilder();
            char? quote = null;
            bool has = false;
            foreach (char c in input)
            {
                if (quote != null)
                {
                    if (c == quote) quote = null; else sb.Append(c);
                }
                else if (c == '"' || c == '\'')
                {
                    quote = c; has = true;
                }
                else if (char.IsWhiteSpace(c))
                {
                    if (has || sb.Length > 0) { tokens.Add(sb.ToString()); sb.Clear(); has = false; }
                }
                else
                {
                    sb.Append(c); has = true;
                }
            }
            if (has || sb.Length > 0) tokens.Add(sb.ToString());
            return tokens;
        }

        /// <summary>Parses the CLI JSON at <paramref name="jsonPath"/>; supports {KEY:VALUE} objects and [{key,value}] arrays.</summary>
        public static IReadOnlyDictionary<string, string> Parse(string output, string jsonPath)
        {
            var secrets = new Dictionary<string, string>();
            if (string.IsNullOrWhiteSpace(output)) return secrets;

            JToken? node;
            try { node = JToken.Parse(output); }
            catch (Exception e) { throw new FetchException($"Failed to parse CLI JSON output: {e.Message}"); }

            if (!string.IsNullOrWhiteSpace(jsonPath) && jsonPath != "$")
            {
                foreach (var part in jsonPath.TrimStart('$').Split('.').Where(p => p.Length > 0))
                {
                    node = node?[part];
                }
            }

            if (node is JArray arr)
            {
                foreach (var item in arr)
                {
                    string? k = (string?)(item["key"] ?? item["secretKey"] ?? item["secretName"]);
                    string? v = (string?)(item["value"] ?? item["secretValue"]);
                    if (k != null && v != null) secrets[k] = v;
                }
            }
            else if (node is JObject obj)
            {
                foreach (var prop in obj.Properties())
                {
                    secrets[prop.Name] = prop.Value?.ToString() ?? "";
                }
            }
            return secrets;
        }

        /// <summary>Windows command-line argument quoting (CommandLineToArgvW rules).</summary>
        private static string QuoteArg(string arg)
        {
            if (arg.Length > 0 && arg.All(c => !char.IsWhiteSpace(c) && c != '"')) return arg;

            var sb = new StringBuilder();
            sb.Append('"');
            int backslashes = 0;
            foreach (char c in arg)
            {
                if (c == '\\')
                {
                    backslashes++;
                }
                else if (c == '"')
                {
                    sb.Append('\\', backslashes * 2 + 1);
                    sb.Append('"');
                    backslashes = 0;
                }
                else
                {
                    if (backslashes > 0) { sb.Append('\\', backslashes); backslashes = 0; }
                    sb.Append(c);
                }
            }
            sb.Append('\\', backslashes * 2);
            sb.Append('"');
            return sb.ToString();
        }
    }
}
