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

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.kivi.secretloader.execution.SecretLoaderProjectCache
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class SecretLoaderConfigurable : Configurable {
    private var panel: JPanel? = null

    private val cliPathField = JBTextField()
    private val commandTemplateField = JBTextField()
    private val jsonPathField = JBTextField()
    private val listProjectsField = JBTextField()
    private val defaultEnvironmentField = JBTextField()
    private val presetCombo = JComboBox(VaultPresets.names())

    private val enableCacheBox = JBCheckBox("Enable in-memory cache")
    private val cacheDurationField = JBTextField()
    private val timeoutField = JBTextField()

    private val strictModeBox = JBCheckBox("Strict mode — block launch if secrets fail to load (0 keys is allowed)")
    private val maskSecretsBox = JBCheckBox("Mask secrets in console output (best-effort)")
    private val preferLocalBox = JBCheckBox("Prefer local variables (don't overwrite run-config vars)")
    private val scanCommitsBox = JBCheckBox("Scan commits for known secret values (pre-commit guard)")
    private val blacklistArea = JBTextArea(4, 40)

    override fun getDisplayName(): String = "SecretLoader"

    override fun createComponent(): JComponent {
        presetCombo.addActionListener {
            VaultPresets.byName(presetCombo.selectedItem as String)?.let {
                if (it.name != "Custom") {
                    cliPathField.text = it.cliPath
                    commandTemplateField.text = it.commandTemplate
                    jsonPathField.text = it.jsonPath
                    listProjectsField.text = it.listProjectsCommand
                }
            }
        }
        val p = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Vault preset: "), presetCombo, 1, false)
            .addLabeledComponent(JBLabel("CLI executable path: "), cliPathField, 1, false)
            .addLabeledComponent(JBLabel("Command template: "), commandTemplateField, 1, false)
            .addTooltip("Placeholders: {cli} {env} {project}. Must print JSON. Quote args with spaces.")
            .addLabeledComponent(JBLabel("JSON path: "), jsonPathField, 1, false)
            .addTooltip("Path to the secrets map. Default \$ (root). Example: \$.data.data")
            .addLabeledComponent(JBLabel("List projects command: "), listProjectsField, 1, false)
            .addTooltip("Optional. A command that prints projects as JSON, used by the 'Pick Project' picker. Leave blank to disable.")
            .addLabeledComponent(JBLabel("Default environment: "), defaultEnvironmentField, 1, false)
            .addSeparator()
            .addComponent(enableCacheBox)
            .addLabeledComponent(JBLabel("Cache duration (minutes): "), cacheDurationField, 1, false)
            .addLabeledComponent(JBLabel("Execution timeout (seconds): "), timeoutField, 1, false)
            .addSeparator()
            .addComponent(strictModeBox)
            .addComponent(maskSecretsBox)
            .addComponent(preferLocalBox)
            .addComponent(scanCommitsBox)
            .addLabeledComponent(JBLabel("Blacklist (key names / globs, one per line): "), blacklistArea, 1, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = p
        reset()
        return p
    }

    override fun isModified(): Boolean {
        val s = SecretLoaderSettings.instance.state
        return cliPathField.text != s.cliPath ||
            commandTemplateField.text != s.commandTemplate ||
            jsonPathField.text != s.jsonPath ||
            listProjectsField.text != s.listProjectsCommand ||
            defaultEnvironmentField.text != s.defaultEnvironment ||
            (presetCombo.selectedItem as String) != s.vaultPreset ||
            enableCacheBox.isSelected != s.enableCache ||
            cacheDurationField.text != s.cacheDurationMinutes.toString() ||
            timeoutField.text != s.timeoutSeconds.toString() ||
            strictModeBox.isSelected != s.strictMode ||
            maskSecretsBox.isSelected != s.maskSecretsInConsole ||
            preferLocalBox.isSelected != s.preferLocalVariables ||
            scanCommitsBox.isSelected != s.scanCommitsForSecrets ||
            blacklistArea.text != s.blacklistRaw
    }

    override fun apply() {
        val s = SecretLoaderSettings.instance.state
        s.cliPath = cliPathField.text
        s.commandTemplate = commandTemplateField.text
        s.jsonPath = jsonPathField.text
        s.listProjectsCommand = listProjectsField.text
        s.defaultEnvironment = defaultEnvironmentField.text
        s.vaultPreset = presetCombo.selectedItem as String
        s.enableCache = enableCacheBox.isSelected
        s.cacheDurationMinutes = cacheDurationField.text.toLongOrNull() ?: 5L
        s.timeoutSeconds = timeoutField.text.toIntOrNull() ?: 10
        s.strictMode = strictModeBox.isSelected
        s.maskSecretsInConsole = maskSecretsBox.isSelected
        s.preferLocalVariables = preferLocalBox.isSelected
        s.scanCommitsForSecrets = scanCommitsBox.isSelected
        s.blacklistRaw = blacklistArea.text
        // Settings changed → invalidate every open project's cache.
        ProjectManager.getInstance().openProjects.forEach { SecretLoaderProjectCache.getInstance(it).clear() }
    }

    override fun reset() {
        val s = SecretLoaderSettings.instance.state
        // Set the preset first: selecting it fires the listener which fills defaults, then we
        // restore the user's saved values below so customizations are never clobbered on open.
        presetCombo.selectedItem = s.vaultPreset
        cliPathField.text = s.cliPath
        commandTemplateField.text = s.commandTemplate
        jsonPathField.text = s.jsonPath
        listProjectsField.text = s.listProjectsCommand
        defaultEnvironmentField.text = s.defaultEnvironment
        enableCacheBox.isSelected = s.enableCache
        cacheDurationField.text = s.cacheDurationMinutes.toString()
        timeoutField.text = s.timeoutSeconds.toString()
        strictModeBox.isSelected = s.strictMode
        maskSecretsBox.isSelected = s.maskSecretsInConsole
        preferLocalBox.isSelected = s.preferLocalVariables
        scanCommitsBox.isSelected = s.scanCommitsForSecrets
        blacklistArea.text = s.blacklistRaw
    }
}
