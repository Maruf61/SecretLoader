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

package com.kivi.secretloader

import com.kivi.secretloader.execution.EnvInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the reflective env-map discovery used by the dry-run "Shadows local?" column and by
 * prefer-local / SECRETLOADER_ENV detection. Exercises [EnvInjector.collectEnvMap] directly (no
 * IDE Application), which is the read-action-free core behind `findEnvMap`.
 */
class EnvInjectorTest {

    // A public zero-arg getter named getEnvs() — mirrors JVM run configs.
    class GetterConfig(private val envs: Map<String, String>) {
        fun getEnvs(): Map<String, String> = envs
    }

    // A private field named `environmentVariables` and no public getter — pure field-based discovery.
    class FieldConfig(@Suppress("unused") private val environmentVariables: Map<String, String>)

    // A profile whose real env is only reachable by drilling through getLaunchSettingsProfile().
    class Inner(private val environmentVariables: Map<String, String>) {
        fun getEnvironmentVariables(): Map<String, String> = environmentVariables
    }
    class DrillConfig(private val inner: Inner) {
        fun getLaunchSettingsProfile(): Inner = inner
    }

    // The Rider-shaped bug: an EMPTY env field sits on the config, the real vars are deeper.
    class EmptyShallowThenDeep(
        @Suppress("unused") private val env: Map<String, String> = emptyMap(),
        private val inner: Inner,
    ) {
        fun getLaunchSettingsProfile(): Inner = inner
    }

    // Same key at two depths — the shallower (primary) value must win.
    class ShallowAndDeep(
        @Suppress("unused") private val env: Map<String, String>,
        private val inner: Inner,
    ) {
        fun getLaunchSettingsProfile(): Inner = inner
    }

    open class BaseWithEnv(@Suppress("unused") private val envs: Map<String, String>)
    class SubConfig(envs: Map<String, String>) : BaseWithEnv(envs)

    @Test
    fun findsEnvViaGetter() {
        val m = EnvInjector.collectEnvMap(GetterConfig(mapOf("A" to "1")))
        assertEquals("1", m?.get("A"))
    }

    @Test
    fun findsEnvViaField() {
        val m = EnvInjector.collectEnvMap(FieldConfig(mapOf("DB_HOST" to "local")))
        assertEquals("local", m?.get("DB_HOST"))
    }

    @Test
    fun findsEnvByDrilling() {
        val m = EnvInjector.collectEnvMap(DrillConfig(Inner(mapOf("K" to "v"))))
        assertEquals("v", m?.get("K"))
    }

    /** The reported "Shadows local?" bug: an empty shallow field must not hide the deep env map. */
    @Test
    fun emptyShallowFieldDoesNotHideDeepEnv() {
        val m = EnvInjector.collectEnvMap(EmptyShallowThenDeep(inner = Inner(mapOf("DB_HOST" to "local"))))
        assertTrue("deep env var should be visible for shadow detection", m?.containsKey("DB_HOST") == true)
    }

    /** When the same key exists shallow and deep, the shallow (primary) value takes precedence. */
    @Test
    fun shallowValueWinsOverDeep() {
        val cfg = ShallowAndDeep(env = mapOf("E" to "shallow"), inner = Inner(mapOf("E" to "deep")))
        assertEquals("shallow", EnvInjector.collectEnvMap(cfg)?.get("E"))
    }

    @Test
    fun findsEnvDeclaredInSuperclass() {
        val m = EnvInjector.collectEnvMap(SubConfig(mapOf("INHERITED" to "1")))
        assertEquals("1", m?.get("INHERITED"))
    }

    @Test
    fun noEnvReturnsNull() {
        assertNull(EnvInjector.collectEnvMap(Any()))
        assertNull(EnvInjector.collectEnvMap(null))
    }
}
