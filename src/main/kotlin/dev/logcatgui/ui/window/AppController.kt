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
package dev.logcatgui.ui.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.logcatgui.AppServices
import dev.logcatgui.Notification
import dev.logcatgui.adb.Device
import dev.logcatgui.files.LogcatFiles
import dev.logcatgui.platform.FileDialogs
import dev.logcatgui.ui.panel.LogcatPanelState
import dev.logcatgui.ui.panel.PanelHost
import dev.logcatgui.ui.panel.PanelSource
import dev.logcatgui.ui.panel.SplitOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class SettingsPage(val title: String) {
    LOGCAT("Logcat"),
    FORMAT("Logcat Format"),
    COLORS("Color Scheme"),
}

/** Window-level state: tabs, which dialog is open, and the [PanelHost] actions. Main-thread only. */
class AppController(private val services: AppServices) : PanelHost {
    val tabs = TabsState(services)

    var settingsPage by mutableStateOf<SettingsPage?>(null)
    var aboutOpen by mutableStateOf(false)
    var screenshotDevice by mutableStateOf<Device?>(null)
    var recorderDevice by mutableStateOf<Device?>(null)

    /** Parent for native file dialogs; set once the main window exists. */
    var awtWindow: java.awt.Window? = null

    private val lastDirectory: String? get() = services.settings.settings.lastDirectory

    fun openSettings(page: SettingsPage = SettingsPage.LOGCAT) {
        settingsPage = page
    }

    fun rememberDirectory(file: File) {
        services.settings.update { it.copy(lastDirectory = file.parent) }
    }

    override fun importFile() {
        val file = FileDialogs.open(awtWindow, "Import Logs from a File", lastDirectory, listOf(LogcatFiles.EXTENSION, "txt", "log")) ?: return
        rememberDirectory(file)
        openFile(file)
    }

    override fun openFile(file: File) {
        services.scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { LogcatFiles.import(file) }
                val panel = tabs.newPanel(PanelSource.FileSource(file, data.deviceDescription), data.messages, data.filter)
                tabs.newTab(file.name, panel)
            } catch (e: IOException) {
                services.notifications.post("Could not import ${file.name}: ${e.message}", Notification.Kind.ERROR)
            }
        }
    }

    override fun exportPanel(panel: LogcatPanelState) {
        val chosen = FileDialogs.save(awtWindow, "Export Logs to a File", lastDirectory, defaultExportName(panel)) ?: return
        val file = if (chosen.extension.isEmpty()) File(chosen.path + "." + LogcatFiles.EXTENSION) else chosen
        rememberDirectory(file)
        panel.export(file, ::openFile)
    }

    override fun chooseMapping(panel: LogcatPanelState) {
        val file = FileDialogs.open(awtWindow, "Select a Proguard Mapping File", lastDirectory, listOf("txt", "map")) ?: return
        rememberDirectory(file)
        panel.loadMapping(file)
    }

    override fun split(panel: LogcatPanelState, orientation: SplitOrientation) = tabs.split(panel, orientation)

    override fun close(panel: LogcatPanelState) = tabs.closePanel(panel)

    override fun canClose(panel: LogcatPanelState) = tabs.canClosePanel(panel)

    override fun openScreenshot(device: Device) {
        screenshotDevice = device
    }

    override fun openScreenRecorder(device: Device) {
        recorderDevice = device
    }

    override fun openFormatSettings() = openSettings(SettingsPage.FORMAT)

    fun dispose() = tabs.dispose()

    /** Like AS: `<manufacturer>-<model>-Android-<release>_<timestamp>.logcat`. */
    private fun defaultExportName(panel: LogcatPanelState): String {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
        val base = when (val s = panel.source) {
            is PanelSource.FileSource -> s.file.nameWithoutExtension
            is PanelSource.DeviceSource -> panel.device()?.let { d ->
                listOfNotNull(d.manufacturer, d.model ?: d.name, d.release?.let { "Android-$it" }).joinToString("-")
            } ?: "logcat"
        }
        return base.replace(Regex("[^A-Za-z0-9._-]+"), "-") + "_" + time + "." + LogcatFiles.EXTENSION
    }
}
