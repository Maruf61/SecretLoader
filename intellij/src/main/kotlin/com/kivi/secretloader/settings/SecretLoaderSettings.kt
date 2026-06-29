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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level (global) settings. Per-project overrides live in [SecretLoaderProjectSettings].
 */
@State(
    name = "SecretLoaderSettings",
    storages = [Storage("SecretLoaderSettings.xml")]
)
class SecretLoaderSettings : PersistentStateComponent<SecretLoaderSettings.State> {

    class State {
        var cliPath: String = "infisical"
        var commandTemplate: String = "{cli} export --format=json --env={env} --projectId={project}"
        var jsonPath: String = "$"

        /** Optional command that lists projects as JSON (for the project picker). Blank = no picker. */
        var listProjectsCommand: String = ""
        var defaultEnvironment: String = "dev"
        var timeoutSeconds: Int = 10
        var enableCache: Boolean = true
        var cacheDurationMinutes: Long = 5

        /** Fail-closed by default: block the launch if secrets cannot be fetched. */
        var strictMode: Boolean = true
        var maskSecretsInConsole: Boolean = true
        var preferLocalVariables: Boolean = true

        /** Newline- or comma-separated key names / globs that must never be injected. */
        var blacklistRaw: String = ""

        /** Informational: which preset last populated the command template (see VaultPresets). */
        var vaultPreset: String = "Infisical"

        /** Pre-commit guardrail: scan staged changes for known injected secret values. */
        var scanCommitsForSecrets: Boolean = true
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: SecretLoaderSettings
            get() = ApplicationManager.getApplication().getService(SecretLoaderSettings::class.java)

        /** Parsed blacklist patterns (case-insensitive), supporting `*` / `?` globs. */
        fun blacklistPatterns(raw: String): List<Regex> =
            raw.split('\n', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { glob ->
                    val pattern = buildString {
                        append('^')
                        for (c in glob) when (c) {
                            '*' -> append(".*")
                            '?' -> append('.')
                            else -> if (c.isLetterOrDigit() || c == '_') append(c) else append('\\').append(c)
                        }
                        append('$')
                    }
                    Regex(pattern, RegexOption.IGNORE_CASE)
                }
    }
}
