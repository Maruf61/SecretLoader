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

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.Base64
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Per-project runtime state: the secrets cache (race-safe, TTL'd, composite-keyed), the set of
 * currently-known injected secret values (for masking + the pre-commit scanner), and the last
 * launched environment (for the status-bar widget).
 *
 * Project-scoped (NOT a static app-wide map) so two projects sharing an env name never cross secrets.
 */
@Service(Service.Level.PROJECT)
class SecretLoaderProjectCache(@Suppress("UNUSED_PARAMETER") project: Project) {

    private data class Entry(val timestamp: Long, val secrets: Map<String, String>)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Any>()

    private val injectedValues = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var maskingPattern: Pattern? = null
    @Volatile private var maskingDirty = true

    @Volatile var lastLaunchedEnvironment: String? = null

    /**
     * Returns cached secrets for [key] within [ttlMillis], otherwise runs [supplier] exactly once
     * (even under concurrent launches of the same key) and caches the result. [supplier] may throw
     * to signal a fetch failure — failures are never cached and the exception propagates.
     */
    fun getOrCompute(
        key: String,
        ttlMillis: Long,
        useCache: Boolean,
        supplier: () -> Map<String, String>,
    ): Map<String, String> {
        if (useCache) {
            val hit = cache[key]
            if (hit != null && System.currentTimeMillis() - hit.timestamp < ttlMillis) return hit.secrets
        }
        val lock = locks.computeIfAbsent(key) { Any() }
        synchronized(lock) {
            if (useCache) {
                val hit = cache[key]
                if (hit != null && System.currentTimeMillis() - hit.timestamp < ttlMillis) return hit.secrets
            }
            val result = supplier()
            if (useCache) cache[key] = Entry(System.currentTimeMillis(), result)
            return result
        }
    }

    fun clear() {
        cache.clear()
        injectedValues.clear()
        maskingDirty = true
        maskingPattern = null
        lastLaunchedEnvironment = null
    }

    fun recordInjectedValues(values: Collection<String>) {
        var added = false
        for (v in values) {
            // Ignore very short values to avoid masking noise / false positives.
            if (v.length > 3 && injectedValues.add(v)) added = true
        }
        if (added) maskingDirty = true
    }

    fun injectedValues(): Set<String> = injectedValues

    /**
     * A compiled alternation matching every known injected value plus its Base64 and URL-encoded
     * forms, longest-first. Rebuilt lazily when the value set changes. `null` when nothing to mask.
     */
    fun maskingPattern(): Pattern? {
        if (!maskingDirty) return maskingPattern
        synchronized(this) {
            if (!maskingDirty) return maskingPattern
            val variants = LinkedHashSet<String>()
            for (v in injectedValues) {
                if (v.length <= 3) continue
                variants.add(v)
                runCatching { variants.add(Base64.getEncoder().encodeToString(v.toByteArray())) }
                runCatching { variants.add(URLEncoder.encode(v, "UTF-8")) }
            }
            maskingPattern = if (variants.isEmpty()) {
                null
            } else {
                val alternation = variants
                    .filter { it.length > 3 }
                    .sortedByDescending { it.length }
                    .joinToString("|") { Pattern.quote(it) }
                if (alternation.isEmpty()) null else Pattern.compile(alternation)
            }
            maskingDirty = false
            return maskingPattern
        }
    }

    companion object {
        fun getInstance(project: Project): SecretLoaderProjectCache =
            project.getService(SecretLoaderProjectCache::class.java)
    }
}
