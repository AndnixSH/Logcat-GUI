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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Loads and saves [AppSettings] as JSON in the per-user config directory:
 * `%APPDATA%\LogcatGUI` on Windows, `~/Library/Application Support/LogcatGUI` on macOS,
 * and `$XDG_CONFIG_HOME/logcat-gui` (default `~/.config/logcat-gui`) elsewhere.
 *
 * [settings] is Compose state; read and [update] it on the main thread. Writes happen on a
 * background thread, in order.
 */
class SettingsStore(private val file: File = defaultFile()) {

    var settings: AppSettings by mutableStateOf(load())
        private set

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "settings-writer").apply { isDaemon = true } }

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(settings)
        if (updated == settings) return
        settings = updated
        val text = JSON.encodeToString(AppSettings.serializer(), updated)
        writer.execute {
            try {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            } catch (e: IOException) {
                System.err.println("Could not save settings to $file: ${e.message}")
            }
        }
    }

    /** Blocks until pending writes are on disk (tests). */
    internal fun awaitWrites() {
        writer.submit {}.get()
    }

    /** Records a filter the user committed: history (most recent first) and MRU. */
    fun rememberFilter(filter: String) {
        val text = filter.trim()
        update { s ->
            val history = if (text.isEmpty()) s.filterHistory
            else (listOf(text) + s.filterHistory.filter { it != text }).take(LogcatSettings.MAX_HISTORY)
            s.copy(filterHistory = history, lastFilter = text)
        }
    }

    private fun load(): AppSettings = try {
        if (file.isFile) JSON.decodeFromString(AppSettings.serializer(), file.readText()) else AppSettings()
    } catch (e: SerializationException) {
        System.err.println("Ignoring unreadable settings file $file: ${e.message}")
        AppSettings()
    } catch (e: IllegalArgumentException) {
        System.err.println("Ignoring unreadable settings file $file: ${e.message}")
        AppSettings()
    } catch (e: IOException) {
        AppSettings()
    }

    companion object {
        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun defaultFile(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val dir = when {
                os.startsWith("windows") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "LogcatGUI")
                os.startsWith("mac") -> File(home, "Library/Application Support/LogcatGUI")
                else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "logcat-gui")
            }
            return File(dir, "settings.json")
        }
    }
}
