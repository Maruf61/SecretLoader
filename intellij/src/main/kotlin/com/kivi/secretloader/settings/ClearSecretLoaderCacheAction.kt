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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.kivi.secretloader.execution.SecretLoaderProjectCache

class ClearSecretLoaderCacheAction : AnAction("Clear SecretLoader Cache") {
    override fun actionPerformed(e: AnActionEvent) {
        val projects = e.project?.let { listOf(it) } ?: ProjectManager.getInstance().openProjects.toList()
        projects.forEach { SecretLoaderProjectCache.getInstance(it).clear() }
        Messages.showInfoMessage("SecretLoader cache cleared.", "SecretLoader")
    }
}
