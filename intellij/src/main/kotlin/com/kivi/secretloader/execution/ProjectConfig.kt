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
import java.io.File

/**
 * Locates and reads the repo's vault config (`.infisical.json` or generic `.secretloader.json`) and
 * the current git branch. The config holds the project id + optional defaults — never secrets.
 */
object ProjectConfig {
    private val mapper = ObjectMapper()

    /**
     * Vault config files we recognize, in priority order. Infisical (`.infisical.json`) and Doppler
     * (`doppler.yaml`) are files the vault CLI itself reads from its working directory — finding them
     * lets us run the CLI from the right folder so it self-resolves the project (no id needed).
     * `.secretloader.json` is a vault-neutral fallback holding `projectId`.
     */
    private val CONFIG_NAMES = listOf(".infisical.json", ".secretloader.json", "doppler.yaml", ".doppler.yaml")
    private val SKIP_DIRS = setOf(
        ".git", ".idea", ".vs", "node_modules", "bin", "obj", "build", "out", "dist", "target", "packages"
    )

    /**
     * Finds the nearest config file: the base dir first, then up to 3 parent dirs, then a shallow
     * scan of sub-directories (depth ≤ 2, skipping build/vcs noise). Returns null if none found.
     */
    fun findConfigFile(basePath: String): File? {
        val base = File(basePath)

        configIn(base)?.let { return it }

        var parent = base.parentFile
        var up = 0
        while (parent != null && up < 3) {
            configIn(parent)?.let { return it }
            parent = parent.parentFile
            up++
        }

        val queue = ArrayDeque<Pair<File, Int>>()
        childDirs(base).forEach { queue.add(it to 1) }
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            configIn(dir)?.let { return it }
            if (depth < 2) childDirs(dir).forEach { queue.add(it to depth + 1) }
        }
        return null
    }

    /** Directory of the nearest vault config file — the right CWD to run the vault CLI from. */
    fun findConfigDir(basePath: String): String? = findConfigFile(basePath)?.parentFile?.absolutePath

    /** Project id from the nearest config: `projectId` (generic) or `workspaceId` (Infisical). */
    fun detectProjectId(basePath: String): String? =
        findConfigFile(basePath)?.let { f ->
            readRoot(f)?.let { it.get("projectId")?.asText() ?: it.get("workspaceId")?.asText() }
        }?.takeIf { it.isNotBlank() }

    /** Repo default environment from the nearest config (`defaultEnvironment`), if present. */
    fun detectDefaultEnvironment(basePath: String): String? =
        findConfigFile(basePath)?.let { readRoot(it)?.get("defaultEnvironment")?.asText() }?.takeIf { it.isNotBlank() }

    /** Maps the current git branch → environment via `gitBranchToEnvironmentMapping`. */
    fun branchEnvironment(basePath: String): String? {
        val file = findConfigFile(basePath) ?: return null
        val mapping = readRoot(file)?.get("gitBranchToEnvironmentMapping")?.takeIf { it.isObject } ?: return null
        val branch = currentGitBranch(file.parentFile?.absolutePath ?: basePath) ?: return null
        return mapping.get(branch)?.asText()?.takeIf { it.isNotBlank() }
    }

    private fun configIn(dir: File): File? =
        CONFIG_NAMES.asSequence().map { dir.resolve(it) }.firstOrNull { it.isFile }

    private fun childDirs(dir: File): List<File> =
        dir.listFiles()?.filter { it.isDirectory && it.name !in SKIP_DIRS && !it.name.startsWith(".") } ?: emptyList()

    private fun readRoot(file: File) = runCatching { mapper.readTree(file) }.getOrNull()

    /** Reads the current branch from `.git/HEAD`, walking up from [startPath] to find the repo. */
    private fun currentGitBranch(startPath: String): String? {
        var dir: File? = File(startPath)
        var up = 0
        while (dir != null && up < 6) {
            val head = File(dir, ".git/HEAD")
            if (head.isFile) {
                val line = runCatching { head.readText().trim() }.getOrNull() ?: return null
                return if (line.startsWith("ref:")) line.substringAfterLast('/') else null
            }
            dir = dir.parentFile
            up++
        }
        return null
    }
}
