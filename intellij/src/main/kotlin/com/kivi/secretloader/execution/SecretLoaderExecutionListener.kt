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

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.WindowManager
import com.kivi.secretloader.settings.SecretLoaderProjectSettings
import com.kivi.secretloader.settings.SecretLoaderSettings

/**
 * Intercepts every run/debug launch, fetches secrets, and injects them into the process env
 * in-memory before it starts. Native debugging binds and Stop is clean (no wrapper process).
 */
class SecretLoaderExecutionListener : ExecutionListener {
    private val LOG = Logger.getInstance(SecretLoaderExecutionListener::class.java)

    companion object {
        private val ABORT = Key.create<Boolean>("SecretLoader.Abort")
        private val UNDO = Key.create<() -> Unit>("SecretLoader.Undo")
    }

    override fun processStarting(executorId: String, env: ExecutionEnvironment) {
        val project = env.project
        val global = SecretLoaderSettings.instance.state
        val projectSettings = SecretLoaderProjectSettings.getInstance(project).state
        val cache = SecretLoaderProjectCache.getInstance(project)

        val failClosed = projectSettings.strictMode ?: global.strictMode
        val preferLocal = projectSettings.preferLocalVariables ?: global.preferLocalVariables

        val basePath = project.basePath ?: return
        val profile = env.runProfile
        val existing = EnvInjector.findEnvMap(profile) ?: emptyMap()

        if (existing["SECRETLOADER_DISABLE"] == "true") {
            LOG.debug("secretloader: disabled via SECRETLOADER_DISABLE for this run.")
            return
        }

        // Environment precedence: per-profile SECRETLOADER_ENV > project override > git-branch map > global default.
        val targetEnv = existing["SECRETLOADER_ENV"]?.toString()?.takeIf { it.isNotBlank() }
            ?: projectSettings.projectEnvironment.takeIf { it.isNotBlank() }
            ?: ProjectConfig.branchEnvironment(basePath)
            ?: ProjectConfig.detectDefaultEnvironment(basePath)
            ?: global.defaultEnvironment
        cache.lastLaunchedEnvironment = targetEnv
        updateWidget(project)

        // Run the vault CLI from the directory holding its config file, so it can self-resolve the project.
        val configDir = ProjectConfig.findConfigDir(basePath)
        val workingDir = configDir ?: basePath

        // Zero-config: if no project id is set, auto-detect it from the nearest config file.
        val projectId = projectSettings.projectId.ifBlank { ProjectConfig.detectProjectId(basePath) ?: "" }

        // Project id required by the template, unavailable, AND no config file for the CLI to self-resolve.
        if (global.commandTemplate.contains("{project}") && projectId.isBlank() && configDir == null) {
            if (failClosed) {
                env.putUserData(ABORT, true)
                Notifier.error(project, "Project ID is required but not set. Launch blocked (strict mode). " +
                        "Set it via the SecretLoader status-bar widget (Pick… or Auto-Detect).")
            } else {
                Notifier.warn(project, "Project ID not set — secrets were not injected.")
            }
            return
        }

        // Fetch (cached, race-safe). On failure: fail-closed blocks the launch; otherwise warn and continue.
        val ttl = global.cacheDurationMinutes * 60_000
        val key = "$projectId|$workingDir|$targetEnv|${global.commandTemplate}"
        val secrets: Map<String, String> = try {
            cache.getOrCompute(key, ttl, global.enableCache) {
                SecretFetcher.fetchBlocking(project, global, targetEnv, projectId, workingDir)
            }
        } catch (e: FetchException) {
            if (failClosed) {
                env.putUserData(ABORT, true)
                Notifier.error(project, "Failed to load secrets for '$targetEnv'. Launch blocked (strict mode).\n${e.message}")
            } else {
                Notifier.warn(project, "Failed to load secrets for '$targetEnv' — launching without them.\n${e.message}")
            }
            return
        }

        // 0 keys is a valid, allowed outcome (do NOT block).
        if (secrets.isEmpty()) {
            Notifier.info(project, "0 secrets for '$targetEnv' (nothing to inject).")
            return
        }

        // Blacklist (global) — never inject these keys.
        val blacklist = SecretLoaderSettings.blacklistPatterns(global.blacklistRaw)
        var blacklisted = 0
        var localSkipped = 0
        val toInject = LinkedHashMap<String, String>()
        for ((k, v) in secrets) {
            if (blacklist.any { it.matches(k) }) { blacklisted++; continue }
            if (preferLocal && existing.containsKey(k)) { localSkipped++; continue }
            toInject[k] = v
        }

        if (toInject.isEmpty()) {
            Notifier.info(project, "No secrets injected for '$targetEnv' " +
                    "(${secrets.size} fetched, $blacklisted blacklisted, $localSkipped kept local).")
            return
        }

        val injection = EnvInjector.inject(profile, toInject)
        if (injection == null) {
            // Loud failure notice: a future IDE version or an unusual config type broke reflection.
            Notifier.warn(project, "Could not find where to inject secrets for this run configuration " +
                    "(${profile.javaClass.simpleName}). Secrets were NOT injected.")
            LOG.warn("secretloader: no env map found on ${profile.javaClass.name}")
            return
        }

        // Record values for masking, and stash undo so we can strip secrets from the (possibly
        // persistent) run config after the run ends — keeps secrets off disk (workspace.xml).
        cache.recordInjectedValues(toInject.values)
        env.putUserData(UNDO, injection.undo)

        // Diagnostic only (debug level — kept out of idea.log by default): which object holds the env map.
        if (LOG.isDebugEnabled) {
            LOG.debug("secretloader: injected ${toInject.size} secrets for '$targetEnv' into ${injection.ownerClass}")
        }

        val skips = buildString {
            if (blacklisted > 0) append(", $blacklisted blacklisted")
            if (localSkipped > 0) append(", $localSkipped kept local")
        }
        Notifier.info(project, "Injected ${toInject.size} secrets for '$targetEnv'$skips.")
    }

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        if (env.getUserData(ABORT) == true) {
            LOG.warn("secretloader: aborting launch (strict mode).")
            handler.destroyProcess()
        }
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        // Restore the run config's env map so injected secrets never persist to disk.
        env.getUserData(UNDO)?.let {
            it.invoke()
            env.putUserData(UNDO, null)
        }
    }

    private fun updateWidget(project: com.intellij.openapi.project.Project) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            WindowManager.getInstance().getStatusBar(project)?.updateWidget("SecretLoaderWidget")
        }
    }
}
