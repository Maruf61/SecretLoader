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

package com.kivi.secretloader.settings

/**
 * Convenience presets that populate the generic CLI path + command template + jsonPath + the optional
 * "list projects" command for a few known vaults. The engine stays fully generic — these just fill the
 * existing fields from a dropdown.
 */
data class VaultPreset(
    val name: String,
    val cliPath: String,
    val commandTemplate: String,
    val jsonPath: String,
    val listProjectsCommand: String,
)

object VaultPresets {
    val ALL: List<VaultPreset> = listOf(
        // Infisical CLI has no project-list command — picking is via `infisical init` (interactive).
        VaultPreset(
            "Infisical",
            "infisical",
            "{cli} export --format=json --env={env} --projectId={project}",
            "$",
            "",
        ),
        VaultPreset(
            "HashiCorp Vault",
            "vault",
            "{cli} kv get -format=json -mount=secret {project}/{env}",
            "$.data.data",
            "{cli} kv list -format=json secret/",
        ),
        VaultPreset(
            "Doppler",
            "doppler",
            "{cli} secrets download --no-file --format json -p {project} -c {env}",
            "$",
            "{cli} projects --json",
        ),
        VaultPreset(
            "AWS Secrets Manager",
            "aws",
            "{cli} secretsmanager get-secret-value --secret-id {project}/{env} --query SecretString --output text",
            "$",
            "{cli} secretsmanager list-secrets --query SecretList[].Name --output json",
        ),
        VaultPreset(
            "Custom",
            "infisical",
            "{cli} export --format=json --env={env}",
            "$",
            "",
        ),
    )

    fun byName(name: String): VaultPreset? = ALL.firstOrNull { it.name == name }
    fun names(): Array<String> = ALL.map { it.name }.toTypedArray()
}
