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

import com.kivi.secretloader.execution.ProjectConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectConfigTest {
    private val temp: File = Files.createTempDirectory("secretloader-test").toFile()

    @After fun cleanup() { temp.deleteRecursively() }

    @Test
    fun detectsInfisicalWorkspaceIdAndDefaultEnv() {
        File(temp, ".infisical.json").writeText("""{"workspaceId":"WS123","defaultEnvironment":"prod"}""")
        assertEquals("WS123", ProjectConfig.detectProjectId(temp.absolutePath))
        assertEquals("prod", ProjectConfig.detectDefaultEnvironment(temp.absolutePath))
    }

    @Test
    fun detectsGenericSecretloaderProjectId() {
        File(temp, ".secretloader.json").writeText("""{"projectId":"P9"}""")
        assertEquals("P9", ProjectConfig.detectProjectId(temp.absolutePath))
    }

    @Test
    fun findsConfigInSubdirectory() {
        val nested = File(temp, "src/app").apply { mkdirs() }
        File(nested, ".secretloader.json").writeText("""{"projectId":"NESTED"}""")
        assertEquals("NESTED", ProjectConfig.detectProjectId(temp.absolutePath))
    }

    @Test
    fun skipsNoiseDirectories() {
        File(temp, "node_modules/pkg").apply { mkdirs() }
        File(temp, "node_modules/pkg/.infisical.json").writeText("""{"workspaceId":"SHOULD_NOT_FIND"}""")
        assertNull(ProjectConfig.detectProjectId(temp.absolutePath))
    }

    @Test
    fun noConfigReturnsNull() {
        assertNull(ProjectConfig.detectProjectId(temp.absolutePath))
    }
}
