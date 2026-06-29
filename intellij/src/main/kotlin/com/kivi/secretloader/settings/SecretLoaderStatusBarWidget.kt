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
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import com.kivi.secretloader.execution.ProjectConfig
import com.kivi.secretloader.execution.ProjectLister
import com.kivi.secretloader.execution.SecretLoaderProjectCache
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridLayout
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*

class SecretLoaderStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null

    override fun ID(): String = "SecretLoaderWidget"
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun install(statusBar: StatusBar) { this.statusBar = statusBar }
    override fun dispose() { statusBar = null }
    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getTooltipText(): String {
        val state = SecretLoaderProjectSettings.getInstance(project).state
        val global = SecretLoaderSettings.instance.state
        val env = state.projectEnvironment.takeIf { it.isNotBlank() } ?: global.defaultEnvironment
        val projId = state.projectId.takeIf { it.isNotBlank() } ?: "None"
        return "<html><b>SecretLoader</b><br/>Environment: $env<br/>Project ID: $projId</html>"
    }

    override fun getText(): String {
        val state = SecretLoaderProjectSettings.getInstance(project).state
        val global = SecretLoaderSettings.instance.state
        val env = SecretLoaderProjectCache.getInstance(project).lastLaunchedEnvironment
            ?: state.projectEnvironment.takeIf { it.isNotBlank() }
            ?: global.defaultEnvironment
        val projId = state.projectId
        return if (projId.isNotBlank()) "Secrets: $env | $projId" else "Secrets: $env"
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { e ->
        val state = SecretLoaderProjectSettings.getInstance(project).state
        val global = SecretLoaderSettings.instance.state

        val panel = JPanel(GridLayout(0, 1))
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val envField = JTextField(state.projectEnvironment, 15)
        panel.add(JLabel("Project Env (empty = global default):"))
        panel.add(envField)

        val projectIdField = JTextField(state.projectId, 15)
        // Persist a detected/picked id immediately so the current project id updates even before Save.
        val applyId: (String) -> Unit = { id ->
            projectIdField.text = id
            state.projectId = id
            SecretLoaderProjectCache.getInstance(project).clear()
            statusBar?.updateWidget(ID())
        }
        val idRow = JPanel(BorderLayout())
        idRow.add(JLabel("Project ID:"), BorderLayout.WEST)
        val buttons = JPanel()

        val pick = JButton("Pick…")
        pick.isFocusable = false
        pick.toolTipText = "List projects from the vault and choose one"
        pick.addActionListener { onPickProject(envField, pick, applyId) }
        buttons.add(pick)

        val autoDetect = JButton("Auto-Detect")
        autoDetect.isFocusable = false
        autoDetect.addActionListener {
            val basePath = project.basePath ?: return@addActionListener
            val file = ProjectConfig.findConfigFile(basePath)
            if (file == null) {
                Messages.showWarningDialog(
                    project, "No .infisical.json or .secretloader.json found near the project root.", "Auto-Detect"
                )
                return@addActionListener
            }
            val id = ProjectConfig.detectProjectId(basePath)
            if (id.isNullOrBlank()) {
                Messages.showWarningDialog(project, "Found ${file.name} but it has no projectId / workspaceId.", "Auto-Detect")
                return@addActionListener
            }
            applyId(id)
            // Also offer the repo's default environment if the env field is still empty.
            if (envField.text.isBlank()) ProjectConfig.detectDefaultEnvironment(basePath)?.let { envField.text = it }
            val where = runCatching { File(basePath).toURI().relativize(file.toURI()).path }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: file.name
            Messages.showInfoMessage(project, "Detected & applied project id from $where.", "Auto-Detect")
        }
        buttons.add(autoDetect)
        idRow.add(buttons, BorderLayout.EAST)
        panel.add(idRow)
        panel.add(projectIdField)

        val cbStrict = JCheckBox("Strict mode", state.strictMode ?: global.strictMode)
        val cbMask = JCheckBox("Mask secrets in console", state.maskSecretsInConsole ?: global.maskSecretsInConsole)
        val cbLocal = JCheckBox("Prefer local variables", state.preferLocalVariables ?: global.preferLocalVariables)
        panel.add(cbStrict); panel.add(cbMask); panel.add(cbLocal)

        val saveBtn = JButton("Save")
        val settingsBtn = JButton("Global Settings…")
        val bottomRow = JPanel()
        bottomRow.add(saveBtn)
        bottomRow.add(settingsBtn)
        panel.add(bottomRow)

        val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, envField)
            .setTitle("SecretLoader Project Settings")
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(false)  // keep open while Pick/Auto-Detect dialogs are shown
            .createPopup()

        saveBtn.addActionListener {
            state.projectEnvironment = envField.text.trim()
            state.projectId = projectIdField.text.trim()
            state.strictMode = if (cbStrict.isSelected != global.strictMode) cbStrict.isSelected else null
            state.maskSecretsInConsole = if (cbMask.isSelected != global.maskSecretsInConsole) cbMask.isSelected else null
            state.preferLocalVariables = if (cbLocal.isSelected != global.preferLocalVariables) cbLocal.isSelected else null
            // Project settings changed → invalidate this project's cache.
            SecretLoaderProjectCache.getInstance(project).clear()
            statusBar?.updateWidget(ID())
            popup.cancel()
        }

        settingsBtn.addActionListener {
            popup.cancel()
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SecretLoaderConfigurable::class.java)
        }

        popup.showInCenterOf(e.component)
    }

    /** "Pick…": list projects via the configured command and choose one; else fall back to `init`. */
    private fun onPickProject(envField: JTextField, anchor: JComponent, applyId: (String) -> Unit) {
        val global = SecretLoaderSettings.instance.state
        val basePath = project.basePath ?: return
        val workingDir = ProjectConfig.findConfigDir(basePath) ?: basePath

        if (global.listProjectsCommand.isBlank()) {
            if (global.cliPath.contains("infisical", ignoreCase = true)) {
                offerInfisicalInit(workingDir, global.cliPath)
            } else {
                Messages.showInfoMessage(
                    project,
                    "No 'List projects command' is configured for this vault. Set one in " +
                        "Settings → Tools → SecretLoader, or use Auto-Detect.",
                    "Pick Project"
                )
            }
            return
        }

        val env = envField.text.trim().ifBlank { global.defaultEnvironment }
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { ProjectLister.listBlocking(project, global, env, workingDir) }
            ApplicationManager.getApplication().invokeLater {
                result.onSuccess { items ->
                    if (items.isEmpty()) {
                        Messages.showWarningDialog(project, "The list command returned no projects.", "Pick Project")
                        return@invokeLater
                    }
                    val labels = items.map { if (it.id == it.label) it.id else "${it.label}  (${it.id})" }
                    JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(labels)
                        .setTitle("Select a project")
                        .setItemChosenCallback { chosen ->
                            val idx = labels.indexOf(chosen)
                            if (idx >= 0) applyId(items[idx].id)
                        }
                        .createPopup()
                        .showInCenterOf(anchor)
                }.onFailure { e ->
                    Messages.showErrorDialog(project, e.message ?: e.toString(), "Pick Project")
                }
            }
        }
    }

    /** Infisical has no list command: open a terminal for the interactive `infisical init` picker. */
    private fun offerInfisicalInit(workingDir: String, cliPath: String) {
        val answer = Messages.showYesNoDialog(
            project,
            "Infisical project selection is interactive. Open a terminal to run '$cliPath init' in:\n" +
                "$workingDir\n\nAfter selecting your project, click Auto-Detect to import it.",
            "Run infisical init", "Open Terminal", "Cancel", Messages.getQuestionIcon()
        )
        if (answer != Messages.YES) return
        if (!launchTerminal(workingDir, "$cliPath init")) {
            Messages.showInfoMessage(
                project,
                "Couldn't open a terminal automatically. Run this manually, then click Auto-Detect:\n\n" +
                    "cd \"$workingDir\"\n$cliPath init",
                "Run infisical init"
            )
        }
    }

    private fun launchTerminal(workingDir: String, command: String): Boolean = runCatching {
        val pb = when {
            SystemInfo.isWindows -> ProcessBuilder("cmd", "/c", "start", "", "cmd", "/k", command)
            SystemInfo.isMac -> ProcessBuilder(
                "osascript", "-e",
                "tell application \"Terminal\" to do script \"cd '$workingDir' && $command\""
            )
            else -> ProcessBuilder("x-terminal-emulator", "-e", "bash", "-lc", "cd '$workingDir' && $command; exec bash")
        }
        pb.directory(File(workingDir)).start()
        true
    }.getOrDefault(false)
}
