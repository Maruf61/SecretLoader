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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.kivi.secretloader.settings.SecretLoaderSettings
import java.io.File
import java.util.concurrent.TimeUnit

/** Thrown when secrets cannot be fetched/parsed. The listener decides fail-closed vs fail-open. */
class FetchException(message: String) : Exception(message)

object SecretFetcher {
    private val LOG = Logger.getInstance(SecretFetcher::class.java)
    private val mapper = ObjectMapper()

    /** Reject whitespace and anything that could inject extra CLI arguments. */
    private val SAFE_ARG = Regex("^[A-Za-z0-9_.:/@-]+$")

    /**
     * Runs the configured CLI and returns the parsed secrets. Blocks the caller (the launch is
     * gated on this), showing a cancellable modal progress dialog when invoked on the UI thread so
     * the IDE never freezes. Throws [FetchException] on any failure (caller enforces fail policy).
     */
    fun fetchBlocking(
        project: Project,
        settings: SecretLoaderSettings.State,
        env: String,
        projectId: String,
        basePath: String,
    ): Map<String, String> {
        val holder = arrayOfNulls<Any>(1)
        val work = Runnable {
            holder[0] = try {
                runCli(settings, env, projectId, basePath)
            } catch (t: Throwable) {
                t
            }
        }

        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                work, "SecretLoader: fetching secrets…", true, project
            )
            if (!completed) throw FetchException("Secret fetch was cancelled.")
        } else {
            work.run()
        }

        return when (val r = holder[0]) {
            is Throwable -> throw if (r is FetchException) r else FetchException(r.message ?: r.toString())
            null -> throw FetchException("Secret fetch produced no result.")
            else -> @Suppress("UNCHECKED_CAST") (r as Map<String, String>)
        }
    }

    private fun runCli(
        settings: SecretLoaderSettings.State,
        env: String,
        projectId: String,
        basePath: String,
    ): Map<String, String> {
        val command = buildCommand(settings, env, projectId)
        if (command.isEmpty()) throw FetchException("Command is empty after template substitution.")

        val process = ProcessBuilder(command)
            .directory(File(basePath))
            .start()

        val finished = process.waitFor(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw FetchException("CLI timed out after ${settings.timeoutSeconds}s.")
        }
        if (process.exitValue() != 0) {
            val err = process.errorStream.bufferedReader().readText().trim().take(500)
            throw FetchException("CLI exited ${process.exitValue()}: $err")
        }
        val output = process.inputStream.bufferedReader().readText()
        return parse(output, settings.jsonPath)
    }

    fun buildCommand(settings: SecretLoaderSettings.State, env: String, projectId: String): List<String> =
        buildCommandFrom(settings.cliPath, settings.commandTemplate, env, projectId)

    /**
     * Tokenizes [template] (quote-aware), substitutes `{cli}` as a single token (so paths with spaces
     * survive) and `{env}`/`{project}` after validating them against argument injection. When
     * [projectId] is blank, `{project}` tokens are dropped (with a preceding bare flag) so the vault
     * CLI can self-resolve the project from its config file in the working directory.
     */
    fun buildCommandFrom(cliPath: String, template: String, env: String, projectId: String): List<String> {
        if (env.isNotEmpty() && !SAFE_ARG.matches(env)) {
            throw FetchException("Unsafe environment name '$env' (only [A-Za-z0-9_.:/@-] allowed).")
        }
        if (projectId.isNotEmpty() && !SAFE_ARG.matches(projectId)) {
            throw FetchException("Unsafe project id (only [A-Za-z0-9_.:/@-] allowed).")
        }
        val dropProject = projectId.isBlank()
        val out = ArrayList<String>()
        for (token in tokenize(template)) {
            when {
                token == "{cli}" -> out.add(cliPath)                       // kept whole — never re-split
                token.contains("{project}") && dropProject -> {
                    // Drop the placeholder; if it's a bare token preceded by a flag, drop the flag too.
                    if (token == "{project}" && out.isNotEmpty() && out.last().startsWith("-") && !out.last().contains("=")) {
                        out.removeAt(out.size - 1)
                    }
                }
                else -> out.add(token.replace("{env}", env).replace("{project}", projectId))
            }
        }
        return out.filter { it.isNotBlank() }
    }

    /** Minimal POSIX-ish tokenizer honoring single and double quotes. */
    fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var quote: Char? = null
        var has = false
        for (c in input) {
            when {
                quote != null -> if (c == quote) quote = null else sb.append(c)
                c == '"' || c == '\'' -> { quote = c; has = true }
                c.isWhitespace() -> if (has || sb.isNotEmpty()) { tokens.add(sb.toString()); sb.setLength(0); has = false }
                else -> { sb.append(c); has = true }
            }
        }
        if (has || sb.isNotEmpty()) tokens.add(sb.toString())
        return tokens
    }

    /** Parses the CLI JSON at [jsonPath]; supports both `{KEY:VALUE}` objects and `[{key,value}]` arrays. */
    fun parse(output: String, jsonPath: String): Map<String, String> {
        if (output.isBlank()) return emptyMap()
        val secrets = LinkedHashMap<String, String>()
        try {
            var node: JsonNode? = mapper.readTree(output)
            if (jsonPath.isNotBlank() && jsonPath != "$") {
                for (part in jsonPath.removePrefix("$").split(".").filter { it.isNotBlank() }) {
                    node = node?.get(part)
                }
            }
            when {
                node == null -> Unit
                node.isArray -> node.forEach { item ->
                    val k = item.get("key")?.asText() ?: item.get("secretKey")?.asText() ?: item.get("secretName")?.asText()
                    val v = item.get("value")?.asText() ?: item.get("secretValue")?.asText()
                    if (k != null && v != null) secrets[k] = v
                }
                node.isObject -> node.fields().forEach { (k, v) -> secrets[k] = v.asText() }
            }
        } catch (e: Exception) {
            throw FetchException("Failed to parse CLI JSON output: ${e.message}")
        }
        return secrets
    }
}
