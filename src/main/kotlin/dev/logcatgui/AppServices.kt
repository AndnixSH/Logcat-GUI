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
package dev.logcatgui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.logcatgui.adb.Adb
import dev.logcatgui.adb.DeviceService
import dev.logcatgui.settings.SettingsStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Properties

/** Application-wide singletons, shared by every tab and panel. Main-thread only. */
class AppServices(
    val adb: Adb = Adb.locate(),
    val settings: SettingsStore = SettingsStore(),
) {
    val notifications = NotificationCenter()

    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            notifications.post("Unexpected error: ${e.message ?: e.javaClass.simpleName}", Notification.Kind.ERROR)
        }
    )

    val devices = DeviceService(adb, scope)

    init {
        devices.start()
    }

    fun dispose() {
        scope.cancel()
        adb.destroyAll()
    }
}

val LocalAppServices = staticCompositionLocalOf<AppServices> { error("No AppServices") }

/** The AWT window hosting the current composition; parent for native file dialogs. */
val LocalAwtWindow = staticCompositionLocalOf<java.awt.Window?> { null }

/** A balloon in the bottom-right corner, like IntelliJ's notifications. */
class Notification(
    val text: String,
    val kind: Kind,
    val actions: List<Pair<String, () -> Unit>>,
) {
    enum class Kind { INFO, WARNING, ERROR }
}

class NotificationCenter {
    val active = mutableStateListOf<Notification>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun post(text: String, kind: Notification.Kind = Notification.Kind.INFO, vararg actions: Pair<String, () -> Unit>) {
        val n = Notification(text, kind, actions.toList())
        active += n
        while (active.size > 4) active.removeAt(0)
        scope.launch {
            delay(if (actions.isEmpty()) 5_000 else 12_000)
            active.remove(n)
        }
    }

    fun dismiss(n: Notification) {
        active.remove(n)
    }
}

/** Build info for the About dialog, from `app-info.properties` (filled in by Gradle). */
object AppInfo {
    const val NAME = "Logcat GUI"
    private val props = Properties().apply {
        AppInfo::class.java.getResourceAsStream("/app-info.properties")?.use { load(it) }
    }
    val version: String = props.getProperty("version", "dev")
    val kotlinVersion: String = props.getProperty("kotlinVersion", "?")
    val composeVersion: String = props.getProperty("composeVersion", "?")
}
