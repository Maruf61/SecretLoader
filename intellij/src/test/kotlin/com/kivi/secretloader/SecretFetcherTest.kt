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

import com.kivi.secretloader.execution.FetchException
import com.kivi.secretloader.execution.SecretFetcher
import com.kivi.secretloader.settings.SecretLoaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretFetcherTest {

    @Test
    fun parsesFlatObject() {
        val out = """{"DB":"x","ConnectionStrings__A":"y"}"""
        val m = SecretFetcher.parse(out, "$")
        assertEquals("x", m["DB"])
        assertEquals("y", m["ConnectionStrings__A"])
    }

    @Test
    fun parsesArrayOfKeyValue() {
        val out = """[{"key":"A","value":"1"},{"secretKey":"B","secretValue":"2"}]"""
        val m = SecretFetcher.parse(out, "$")
        assertEquals("1", m["A"])
        assertEquals("2", m["B"])
    }

    @Test
    fun parsesNestedJsonPath() {
        val out = """{"data":{"data":{"A":"1"}}}"""
        val m = SecretFetcher.parse(out, "$.data.data")
        assertEquals("1", m["A"])
    }

    @Test
    fun emptyOutputIsEmptyMap() {
        assertTrue(SecretFetcher.parse("", "$").isEmpty())
    }

    @Test
    fun tokenizerKeepsQuotedSegments() {
        val tokens = SecretFetcher.tokenize("""{cli} export --env={env} "with space"""")
        assertEquals(listOf("{cli}", "export", "--env={env}", "with space"), tokens)
    }

    @Test
    fun cliPathWithSpacesStaysOneToken() {
        val s = SecretLoaderSettings.State().apply {
            cliPath = """C:\Program Files\infisical\infisical.exe"""
            commandTemplate = "{cli} export --env={env}"
        }
        val cmd = SecretFetcher.buildCommand(s, "dev", "")
        assertEquals("""C:\Program Files\infisical\infisical.exe""", cmd[0])
    }

    @Test
    fun rejectsArgumentInjectionInEnv() {
        val s = SecretLoaderSettings.State()
        assertThrows(FetchException::class.java) {
            SecretFetcher.buildCommand(s, "dev --evil", "pid")
        }
    }

    @Test
    fun blacklistGlobMatches() {
        val patterns = SecretLoaderSettings.blacklistPatterns("AWS_*\nDEBUG")
        assertTrue(patterns.any { it.matches("AWS_SECRET") })
        assertTrue(patterns.any { it.matches("DEBUG") })
        assertTrue(patterns.none { it.matches("DB_HOST") })
    }
}
