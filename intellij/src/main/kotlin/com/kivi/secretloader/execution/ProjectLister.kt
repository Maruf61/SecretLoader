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

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.kivi.secretloader.settings.SecretLoaderSettings
import java.io.File
import java.util.concurrent.TimeUnit

/** A selectable project/scope returned by a vault's "list projects" command. */
data class ProjectItem(val id: String, val label: String)

/**
 * Runs the user-configured "List Projects" command (any CLI that prints JSON) and parses it into a
 * pickable list. Vault-agnostic — works for Doppler (`doppler projects --json`), Vault, AWS, or any
 * custom tool. (Infisical's CLI has no list command, so this is left blank for Infisical.)
 */
object ProjectLister {
    private val mapper = ObjectMapper()

    fun listBlocking(
        project: Project,
        settings: SecretLoaderSettings.State,
        env: String,
        workingDir: String,
    ): List<ProjectItem> {
        if (settings.listProjectsCommand.isBlank()) throw FetchException("No 'List Projects' command configured.")

        val holder = arrayOfNulls<Any>(1)
        val work = Runnable {
            holder[0] = try {
                runList(settings, env, workingDir)
            } catch (t: Throwable) {
                t
            }
        }

        if (ApplicationManager.getApplication().isDispatchThread) {
            val ok = ProgressManager.getInstance()
                .runProcessWithProgressSynchronously(work, "SecretLoader: listing projects…", true, project)
            if (!ok) throw FetchException("Listing projects was cancelled.")
        } else {
            work.run()
        }

        return when (val r = holder[0]) {
            is Throwable -> throw if (r is FetchException) r else FetchException(r.message ?: r.toString())
            null -> emptyList()
            else -> @Suppress("UNCHECKED_CAST") (r as List<ProjectItem>)
        }
    }

    private fun runList(settings: SecretLoaderSettings.State, env: String, workingDir: String): List<ProjectItem> {
        val command = SecretFetcher.buildCommandFrom(settings.cliPath, settings.listProjectsCommand, env, "")
        if (command.isEmpty()) throw FetchException("List command is empty after substitution.")

        val process = ProcessBuilder(command).directory(File(workingDir)).start()
        val finished = process.waitFor(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw FetchException("List command timed out after ${settings.timeoutSeconds}s.")
        }
        if (process.exitValue() != 0) {
            val err = process.errorStream.bufferedReader().readText().trim().take(500)
            throw FetchException("List command exited ${process.exitValue()}: $err")
        }
        return parseProjects(process.inputStream.bufferedReader().readText())
    }

    /** Parses array-of-objects, array-of-strings, or an object map into [ProjectItem]s. */
    fun parseProjects(output: String): List<ProjectItem> {
        if (output.isBlank()) return emptyList()
        val items = LinkedHashMap<String, ProjectItem>()  // de-dupe by id, keep first label
        val node = runCatching { mapper.readTree(output) }.getOrNull() ?: return emptyList()
        when {
            node.isArray -> node.forEach { el ->
                when {
                    el.isObject -> {
                        val id = el.get("id")?.asText() ?: el.get("slug")?.asText()
                            ?: el.get("projectId")?.asText() ?: el.get("workspaceId")?.asText()
                            ?: el.get("name")?.asText()
                        val label = el.get("name")?.asText() ?: el.get("slug")?.asText()
                            ?: el.get("title")?.asText() ?: id
                        if (!id.isNullOrBlank()) items.putIfAbsent(id, ProjectItem(id, label ?: id))
                    }
                    el.isValueNode -> el.asText().takeIf { it.isNotBlank() }?.let { items.putIfAbsent(it, ProjectItem(it, it)) }
                }
            }
            node.isObject -> node.fieldNames().forEach { k ->
                val v = node.get(k)
                if (k.isNotBlank()) items.putIfAbsent(k, ProjectItem(k, if (v.isValueNode) v.asText() else k))
            }
        }
        return items.values.toList()
    }
}
