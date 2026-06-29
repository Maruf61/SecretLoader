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

import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.kivi.secretloader.settings.SecretLoaderProjectSettings
import com.kivi.secretloader.settings.SecretLoaderSettings
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * "Dry run" panel: shows the keys that WOULD be injected (with a "Shadows?" column for keys already
 * present in the selected run config), and lets the dev COPY them. Nothing is written to disk.
 */
class SecretLoaderToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        val model = DefaultTableModel(arrayOf("Key", "Shadows local?", "Value"), 0)
        val table = JBTable(model)

        val fetchBtn = JButton("Fetch (Dry Run)")
        val copyKeysBtn = JButton("Copy Keys")
        val copyValuesBtn = JButton("Copy KEY=VALUE")

        fetchBtn.addActionListener {
            model.rowCount = 0
            ApplicationManager.getApplication().executeOnPooledThread {
                val global = SecretLoaderSettings.instance.state
                val projectSettings = SecretLoaderProjectSettings.getInstance(project).state
                val basePath = project.basePath ?: return@executeOnPooledThread

                val selected = RunManager.getInstance(project).selectedConfiguration?.configuration
                val existing = selected?.let { EnvInjector.findEnvMap(it) } ?: emptyMap()
                val configEnv = existing["SECRETLOADER_ENV"]?.toString()?.takeIf { it.isNotBlank() }

                val targetEnv = configEnv
                    ?: projectSettings.projectEnvironment.takeIf { it.isNotBlank() }
                    ?: ProjectConfig.branchEnvironment(basePath)
                    ?: global.defaultEnvironment

                val workingDir = ProjectConfig.findConfigDir(basePath) ?: basePath
                val projectId = projectSettings.projectId.ifBlank { ProjectConfig.detectProjectId(basePath) ?: "" }
                val result = runCatching {
                    SecretFetcher.fetchBlocking(project, global, targetEnv, projectId, workingDir)
                }
                ApplicationManager.getApplication().invokeLater {
                    result.onSuccess { secrets ->
                        for ((k, v) in secrets) {
                            val shadows = if (existing.containsKey(k)) "yes" else ""
                            model.addRow(arrayOf(k, shadows, v))
                        }
                        if (secrets.isEmpty()) model.addRow(arrayOf("(no secrets)", "", "for env '$targetEnv'"))
                    }.onFailure { e ->
                        model.addRow(arrayOf("Error", "", e.message ?: e.toString()))
                    }
                }
            }
        }

        copyKeysBtn.addActionListener {
            val keys = (0 until model.rowCount).joinToString("\n") { model.getValueAt(it, 0).toString() }
            CopyPasteManager.getInstance().setContents(StringSelection(keys))
        }
        copyValuesBtn.addActionListener {
            val lines = (0 until model.rowCount).joinToString("\n") {
                "${model.getValueAt(it, 0)}=${model.getValueAt(it, 2)}"
            }
            CopyPasteManager.getInstance().setContents(StringSelection(lines))
        }

        val buttons = JPanel()
        buttons.add(fetchBtn)
        buttons.add(copyKeysBtn)
        buttons.add(copyValuesBtn)
        panel.add(buttons, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
