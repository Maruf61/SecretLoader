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

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Per-project settings. Boolean overrides are nullable: `null` means "inherit the global value".
 */
@State(name = "SecretLoaderProjectSettings", storages = [Storage("secretLoader.xml")])
@Service(Service.Level.PROJECT)
class SecretLoaderProjectSettings : PersistentStateComponent<SecretLoaderProjectSettings.State> {
    class State {
        var projectEnvironment: String = ""
        var projectId: String = ""
        var strictMode: Boolean? = null
        var maskSecretsInConsole: Boolean? = null
        var preferLocalVariables: Boolean? = null
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(project: Project): SecretLoaderProjectSettings =
            project.getService(SecretLoaderProjectSettings::class.java)
    }
}
