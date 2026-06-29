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

import com.kivi.secretloader.execution.ProjectLister
import com.kivi.secretloader.execution.SecretFetcher
import com.kivi.secretloader.settings.SecretLoaderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectListerTest {

    @Test
    fun parsesArrayOfObjects() {
        val out = """[{"id":"p1","name":"Billing"},{"slug":"p2","name":"Auth"}]"""
        val items = ProjectLister.parseProjects(out)
        assertEquals(2, items.size)
        assertEquals("p1", items[0].id)
        assertEquals("Billing", items[0].label)
        assertEquals("p2", items[1].id)
    }

    @Test
    fun parsesArrayOfStrings() {
        val items = ProjectLister.parseProjects("""["alpha","beta"]""")
        assertEquals(listOf("alpha", "beta"), items.map { it.id })
        assertEquals("alpha", items[0].label)
    }

    @Test
    fun parsesObjectMap() {
        val items = ProjectLister.parseProjects("""{"id1":"Name One","id2":"Name Two"}""")
        assertEquals(2, items.size)
        assertEquals("id1", items[0].id)
        assertEquals("Name One", items[0].label)
    }

    @Test
    fun dedupesById() {
        val items = ProjectLister.parseProjects("""[{"id":"x","name":"A"},{"id":"x","name":"B"}]""")
        assertEquals(1, items.size)
        assertEquals("A", items[0].label)
    }

    @Test
    fun blankProjectDropsProjectFlagToken() {
        // --projectId={project} is one token: dropped entirely when id is blank.
        val cmd = SecretFetcher.buildCommandFrom("infisical", "{cli} export --env={env} --projectId={project}", "dev", "")
        assertEquals(listOf("infisical", "export", "--env=dev"), cmd)
    }

    @Test
    fun blankProjectDropsBareFlagAndValue() {
        // "-p {project}" is two tokens: both the flag and placeholder are dropped when id is blank.
        val cmd = SecretFetcher.buildCommandFrom("doppler", "{cli} secrets -p {project} -c {env}", "dev", "")
        assertEquals(listOf("doppler", "secrets", "-c", "dev"), cmd)
    }

    @Test
    fun presentProjectIsSubstituted() {
        val cmd = SecretFetcher.buildCommandFrom("infisical", "{cli} export --projectId={project}", "dev", "WS1")
        assertEquals(listOf("infisical", "export", "--projectId=WS1"), cmd)
    }
}
