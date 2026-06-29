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

/**
 * Reflective environment-variable injection into a RunProfile's env map.
 *
 * Why reflection: for Rider .NET (the primary target) there is NO public API to patch the launch
 * environment — the official `RunConfigurationExtension.updateJavaParameters` hook is JVM-only.
 * We walk the profile for an env-vars Map and mutate it.
 *
 * [inject] returns an [Injection] whose [Injection.undo] reverts the mutation, so the (potentially
 * persistent) run configuration never retains secret values after the run ends — nothing on disk.
 */
object EnvInjector {
    private val TARGET_FIELDS = listOf("environmentVariables", "envs", "env", "envVars", "myEnvironmentVariables")
    private val GETTERS = listOf("getEnvironmentVariables", "getEnvs", "getEnv")
    private val DRILL = listOf("getParameters", "getRunParameters", "getLaunchSettingsProfile", "getExecutable", "getStartBrowserParameters", "getLaunchSettings")

    data class Injection(val ownerClass: String, val injectedKeys: Set<String>, val undo: () -> Unit)

    /** Read-only: find the first env-vars map on the profile (for prefer-local + dry-run shadowing). */
    fun findEnvMap(obj: Any?, visited: MutableSet<Any> = mutableSetOf()): Map<String, String>? {
        if (obj == null || !visited.add(obj)) return null
        try {
            val mapFields = obj.javaClass.declaredFields.filter { Map::class.java.isAssignableFrom(it.type) }
            for (field in mapFields.sortedByDescending { TARGET_FIELDS.contains(it.name) }) {
                if (!TARGET_FIELDS.contains(field.name)) continue
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val m = field.get(obj) as? Map<String, String>
                if (m != null) return m
            }
        } catch (_: Exception) {}
        for (g in GETTERS) {
            try {
                val getter = obj.javaClass.methods.find { it.name == g && it.parameterCount == 0 } ?: continue
                @Suppress("UNCHECKED_CAST")
                val m = getter.invoke(obj) as? Map<String, String>
                if (m != null) return m
            } catch (_: Exception) {}
        }
        for (d in DRILL) {
            try {
                val method = obj.javaClass.methods.find { it.name == d && it.parameterCount == 0 } ?: continue
                val inner = method.invoke(obj) ?: continue
                findEnvMap(inner, visited)?.let { return it }
            } catch (_: Exception) {}
        }
        return null
    }

    fun inject(profile: Any?, secrets: Map<String, String>): Injection? =
        injectRec(profile, secrets, mutableSetOf())

    private fun injectRec(obj: Any?, secrets: Map<String, String>, visited: MutableSet<Any>): Injection? {
        if (obj == null || !visited.add(obj)) return null

        try {
            val mapFields = obj.javaClass.declaredFields.filter { Map::class.java.isAssignableFrom(it.type) }
            for (field in mapFields.sortedByDescending { TARGET_FIELDS.contains(it.name) }) {
                if (!TARGET_FIELDS.contains(field.name)) continue
                field.isAccessible = true
                val current = field.get(obj) as? Map<*, *>
                if (current is MutableMap<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val map = current as MutableMap<String, String>
                    return Injection(obj.javaClass.name, secrets.keys.toSet(), applyWithUndo(map, secrets))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val original = current as? Map<String, String>
                    val newMap = original?.toMutableMap() ?: mutableMapOf()
                    newMap.putAll(secrets)
                    field.set(obj, newMap)
                    return Injection(obj.javaClass.name, secrets.keys.toSet()) {
                        try { field.set(obj, original) } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        for (g in GETTERS) {
            try {
                val getter = obj.javaClass.methods.find { it.name == g && it.parameterCount == 0 } ?: continue
                val current = getter.invoke(obj)
                if (current is MutableMap<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val map = current as MutableMap<String, String>
                    return Injection(obj.javaClass.name, secrets.keys.toSet(), applyWithUndo(map, secrets))
                } else {
                    val setter = obj.javaClass.methods.find { it.name == "set" + g.substring(3) && it.parameterCount == 1 } ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val original = current as? Map<String, String>
                    val newMap = original?.toMutableMap() ?: mutableMapOf()
                    newMap.putAll(secrets)
                    setter.invoke(obj, newMap)
                    return Injection(obj.javaClass.name, secrets.keys.toSet()) {
                        try { setter.invoke(obj, original ?: emptyMap<String, String>()) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        for (d in DRILL) {
            try {
                val method = obj.javaClass.methods.find { it.name == d && it.parameterCount == 0 } ?: continue
                val inner = method.invoke(obj) ?: continue
                injectRec(inner, secrets, visited)?.let { return it }
            } catch (_: Exception) {}
        }
        return null
    }

    /** Mutates [map] in place and returns an undo that removes added keys and restores overwritten ones. */
    private fun applyWithUndo(map: MutableMap<String, String>, secrets: Map<String, String>): () -> Unit {
        val added = mutableListOf<String>()
        val overwritten = mutableMapOf<String, String>()
        for ((k, v) in secrets) {
            if (map.containsKey(k)) overwritten[k] = map.getValue(k) else added.add(k)
            map[k] = v
        }
        return {
            try {
                added.forEach { map.remove(it) }
                overwritten.forEach { (k, v) -> map[k] = v }
            } catch (_: Exception) {}
        }
    }
}
