/*
 * Copyright 2026 The Logcat-GUI Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.logcatgui.settings

import dev.logcatgui.model.LogLevel
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDirectory("logcat-gui-settings").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun defaultsMatchAndroidStudio() {
        val s = LogcatSettings()
        assertEquals(1024, s.bufferSizeKb)
        assertFalse(s.mostRecentlyUsedFilterIsDefault)
        assertFalse(s.filterHistoryAutocomplete)
        assertFalse(s.overrideFontSize)
        assertTrue(LogcatSettings.isValidBufferSize(102400))
        assertFalse(LogcatSettings.isValidBufferSize(102401))
        assertFalse(LogcatSettings.isValidBufferSize(0))
        assertEquals("BBB529", LogcatColorScheme().forLevel(LogLevel.WARN).message)
    }

    @Test
    fun savesAndReloads() = withTempDir { dir ->
        val file = File(dir, "settings.json")
        val store = SettingsStore(file)
        store.update { it.copy(logcat = it.logcat.copy(bufferSizeKb = 4096, ignoredTags = setOf("chatty"))) }
        store.update { it.copy(format = it.format.copy(defaultStyle = FormatStyle.COMPACT)) }
        store.awaitWrites()

        val reloaded = SettingsStore(file).settings
        assertEquals(4096, reloaded.logcat.bufferSizeKb)
        assertEquals(setOf("chatty"), reloaded.logcat.ignoredTags)
        assertEquals(FormatStyle.COMPACT, reloaded.format.defaultStyle)
    }

    @Test
    fun filterHistoryIsMostRecentFirstWithoutDuplicates() = withTempDir { dir ->
        val store = SettingsStore(File(dir, "settings.json"))
        store.rememberFilter("tag:A")
        store.rememberFilter("tag:B")
        store.rememberFilter(" tag:A ")
        store.rememberFilter("")
        assertEquals(listOf("tag:A", "tag:B"), store.settings.filterHistory)
        assertEquals("", store.settings.lastFilter)
    }

    @Test
    fun ignoresAnUnreadableFile() = withTempDir { dir ->
        val file = File(dir, "settings.json").apply { writeText("{ not json") }
        assertEquals(AppSettings(), SettingsStore(file).settings)
    }

    @Test
    fun toleratesUnknownAndMissingKeys() = withTempDir { dir ->
        val file = File(dir, "settings.json").apply { writeText("""{ "logcat": { "bufferSizeKb": 2048 }, "someFutureKey": 1 }""") }
        val s = SettingsStore(file).settings
        assertEquals(2048, s.logcat.bufferSizeKb)
        assertEquals(FormattingOptions.STANDARD, s.format.standard)
    }
}
