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

package com.kivi.secretloader.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.kivi.secretloader.execution.SecretLoaderProjectCache
import com.kivi.secretloader.settings.SecretLoaderSettings

/** Pre-commit guardrail: blocks committing any currently-known injected secret value. */
class SecretLoaderCheckinHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
        object : CheckinHandler() {
            private val LOG = Logger.getInstance(SecretLoaderCheckinHandlerFactory::class.java)

            override fun beforeCheckin(): ReturnResult {
                if (!SecretLoaderSettings.instance.state.scanCommitsForSecrets) return ReturnResult.COMMIT
                val project = panel.project
                val known = SecretLoaderProjectCache.getInstance(project).injectedValues()
                if (known.isEmpty()) return ReturnResult.COMMIT

                val offenders = LinkedHashSet<String>()
                for (file in panel.virtualFiles) {
                    if (file.isDirectory || file.length > 2_000_000) continue
                    val text = runCatching { String(file.contentsToByteArray()) }.getOrNull() ?: continue
                    if (known.any { it.length > 3 && text.contains(it) }) offenders.add(file.name)
                    if (offenders.size >= 20) break
                }
                if (offenders.isEmpty()) return ReturnResult.COMMIT

                LOG.warn("secretloader: pre-commit scan flagged ${offenders.size} file(s)")
                val answer = Messages.showYesNoDialog(
                    project,
                    "A known secret value appears in:\n  ${offenders.joinToString("\n  ")}\n\n" +
                        "These look like vault secrets that should NOT be committed. Commit anyway?",
                    "SecretLoader: Possible Secret in Commit",
                    "Commit Anyway", "Cancel", Messages.getWarningIcon()
                )
                return if (answer == Messages.YES) ReturnResult.COMMIT else ReturnResult.CANCEL
            }
        }
}
