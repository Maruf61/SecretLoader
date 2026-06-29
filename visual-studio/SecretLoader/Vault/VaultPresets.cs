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

namespace SecretLoader
{
    /// <summary>Quick-start vault configurations selectable from the Options page.</summary>
    public enum VaultPreset
    {
        Custom = 0,
        Infisical,
        Doppler,
        HashiCorpVault,
        AwsSecretsManager,
    }

    /// <summary>
    /// Canonical CLI / command-template / json-path (and optional project-list command) for each preset.
    /// Selecting a preset in the Options page copies these into the editable fields, then snaps the
    /// selector back to Custom so the user can tweak from there.
    /// </summary>
    internal static class VaultPresets
    {
        public static (string Cli, string Template, string JsonPath, string ListProjects) Get(VaultPreset preset)
        {
            switch (preset)
            {
                case VaultPreset.Infisical:
                    return ("infisical",
                            "{cli} export --format=json --env={env} --projectId={project}",
                            "$",
                            ""); // no stable JSON project-list in the CLI; use Auto-Detect from .infisical.json

                case VaultPreset.Doppler:
                    return ("doppler",
                            "{cli} secrets download --no-file --format json --project {project} --config {env}",
                            "$",
                            "{cli} projects --json");

                case VaultPreset.HashiCorpVault:
                    return ("vault",
                            "{cli} kv get -format=json {project}",
                            "$.data.data",
                            "");

                case VaultPreset.AwsSecretsManager:
                    return ("aws",
                            "{cli} secretsmanager get-secret-value --secret-id {project} --query SecretString --output text",
                            "$",
                            "{cli} secretsmanager list-secrets --query SecretList[].Name --output json");

                default:
                    return ("", "", "", "");
            }
        }
    }
}
