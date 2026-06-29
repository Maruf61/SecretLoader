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

package com.kivi.secretloader.execution

import com.intellij.execution.filters.ConsoleInputFilterProvider
import com.intellij.execution.filters.InputFilter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.kivi.secretloader.settings.SecretLoaderProjectSettings
import com.kivi.secretloader.settings.SecretLoaderSettings

/**
 * Best-effort console masking — replaces injected secret values (and their Base64 / URL-encoded
 * forms) with `********`. Like Jenkins/GitHub/GitLab this is defense-in-depth, NOT a guarantee:
 * a process can still defeat it by transforming a value before printing. The real protections are
 * the blacklist and not logging secrets. The compiled pattern lives on the per-project cache.
 */
class SecretLoaderConsoleFilterProvider : ConsoleInputFilterProvider {
    override fun getDefaultFilters(project: Project): Array<InputFilter> {
        return arrayOf(InputFilter { text, contentType: ConsoleViewContentType ->
            val global = SecretLoaderSettings.instance.state
            val proj = SecretLoaderProjectSettings.getInstance(project).state
            val enabled = proj.maskSecretsInConsole ?: global.maskSecretsInConsole
            if (!enabled) return@InputFilter null

            val pattern = SecretLoaderProjectCache.getInstance(project).maskingPattern()
                ?: return@InputFilter null

            val matcher = pattern.matcher(text)
            if (!matcher.find()) return@InputFilter null

            matcher.reset()
            val masked = matcher.replaceAll("********")
            mutableListOf(Pair.create(masked, contentType))
        })
    }
}
