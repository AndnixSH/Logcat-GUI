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
@file:OptIn(ExperimentalComposeUiApi::class)

package dev.logcatgui.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.logcatgui.AppServices
import dev.logcatgui.Notification
import dev.logcatgui.adb.Adb
import dev.logcatgui.adb.Device
import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogcatHeader
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.settings.FormatStyle
import dev.logcatgui.settings.SettingsStore
import dev.logcatgui.ui.components.OverflowToolbar
import dev.logcatgui.ui.dialogs.AboutContent
import dev.logcatgui.ui.dialogs.SettingsContent
import dev.logcatgui.ui.panel.DeviceComboBox
import dev.logcatgui.ui.panel.FilterField
import dev.logcatgui.ui.panel.LocalPanelHost
import dev.logcatgui.ui.panel.LogcatPanelState
import dev.logcatgui.ui.panel.PanelHost
import dev.logcatgui.ui.panel.PanelSource
import dev.logcatgui.ui.panel.SplitOrientation
import dev.logcatgui.ui.panel.toolbarItems
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeTheme
import dev.logcatgui.LocalAppServices
import dev.logcatgui.ui.window.AppController
import dev.logcatgui.ui.window.LeafNode
import dev.logcatgui.ui.window.LogcatWindow
import dev.logcatgui.ui.window.SettingsPage
import dev.logcatgui.ui.window.SplitNode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

/**
 * Renders key UI states offscreen to PNG files (`./gradlew uiSnapshots`), for checking the
 * look without a device or a visible window. Uses fake devices and sample messages; adb is
 * never started.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/ui-snapshots").apply { mkdirs() }
    val tmp = createTempDirectory("logcat-gui-snapshots").toFile()
    val services = AppServices(adb = Adb(null), settings = SettingsStore(File(tmp, "settings.json")))
    services.settings.update {
        it.copy(
            filterHistory = listOf("tag:OkHttp level:debug", "package:com.example.app -tag:Choreographer", "is:crash"),
            favoriteFilters = listOf("level:warn"),
        )
    }
    val snapshots = Snapshots(out, services)
    try {
        snapshots.all()
    } finally {
        tmp.deleteRecursively()
    }
    println("Wrote ${out.listFiles()?.size ?: 0} snapshots to ${out.absolutePath}")
    exitProcess(0)
}

private object NoopHost : PanelHost {
    override fun importFile() {}
    override fun openFile(file: File) {}
    override fun exportPanel(panel: LogcatPanelState) {}
    override fun chooseMapping(panel: LogcatPanelState) {}
    override fun split(panel: LogcatPanelState, orientation: SplitOrientation) {}
    override fun close(panel: LogcatPanelState) {}
    override fun canClose(panel: LogcatPanelState) = true
    override fun openScreenshot(device: Device) {}
    override fun openScreenRecorder(device: Device) {}
    override fun openFormatSettings() {}
}

private class Snapshots(private val out: File, private val services: AppServices) {

    /** The device list from the user's Android Studio screenshot. */
    private val devices = listOf(
        Device("adb-3C161FDJG001EK-DJzriv._adb-tls-connect._tcp", "device", manufacturer = "Google", model = "Pixel 8 Pro", release = "17", apiLevel = "37.1"),
        Device("emulator-5554", "device", manufacturer = "OPPO", model = "CPH1823", release = "12", apiLevel = "32"),
        Device("192.168.50.100:5555", "device", manufacturer = "Sony", model = "G8341", release = "9", apiLevel = "28"),
        Device("emulator-5556", Device.DISCONNECTED, manufacturer = "OPPO", model = "CPH1823", release = "12", apiLevel = "32"),
        Device("ZY227X234X", "device", manufacturer = "motorola", model = "moto g 5G plus", release = "11", apiLevel = "30"),
        Device("QV702XTV0X", "device", manufacturer = "Sony", model = "G8341", release = "9", apiLevel = "28"),
        Device("emulator-5560", "device", manufacturer = "Samsung", model = "SM-S916U", release = "15", apiLevel = "35"),
        Device("adb-R52Y306CTHD-3jbvWR._adb-tls-connect._tcp", "device", manufacturer = "samsung", model = "SM-X710", release = "16", apiLevel = "36.1"),
    )

    fun all() {
        val samples = sampleMessages()
        val devicePanel = LogcatPanelState(services, PanelSource.DeviceSource(devices.last().serial), "", FormatStyle.STANDARD)
        val filePanel = LogcatPanelState(services, PanelSource.FileSource(File("pixel8.logcat"), "Google Pixel 8 Pro Android 17, API 37.1"), "", FormatStyle.STANDARD, samples)

        shot("device-popup", 900, 430, interact = { click(216f, 30f) }) {
            DeviceComboBox(devicePanel, devices, Modifier.padding(start = 16.dp, top = 16.dp).width(400.dp))
        }
        // Toolbar: 5 × 28 + sep 9 + 2 × 28 + sep 9 + format 28 + sep 9 + split …
        shot("toolbar-format-menu", 760, 240, interact = { click(8f + 140 + 9 + 56 + 9 + 14, 24f) }) { toolbar(filePanel, 700) }
        shot("toolbar-split-menu", 760, 240, interact = { click(8f + 140 + 9 + 56 + 9 + 28 + 9 + 14, 24f) }) { toolbar(filePanel, 700) }
        // At 300dp only Clear…Wrap, Import/Export and Format fit (242dp); "more" follows them.
        shot("toolbar-overflow", 520, 420, interact = { click(8f + 242 + 14, 24f) }) { toolbar(filePanel, 300) }

        filePanel.filterText = "tag:"
        shot("filter-completion", 900, 380, interact = {
            click(16f + 300, 30f) // into the text, right of "tag:" so the caret lands at the end
            key(Key.Spacebar, ctrl = true)
        }) {
            FilterField(filePanel, Modifier.padding(start = 16.dp, top = 16.dp).width(600.dp))
        }
        filePanel.filterText = ""
        shot("filter-history", 900, 300, interact = { click(16f + 2 + 12, 30f) }) {
            FilterField(filePanel, Modifier.padding(start = 16.dp, top = 16.dp).width(600.dp))
        }

        for (page in SettingsPage.entries) {
            shot("settings-${page.name.lowercase()}", 900, 700) { SettingsContent(page, onClose = {}) }
        }
        shot("about", 620, 600) { AboutContent(onClose = {}) }

        val controller = AppController(services)
        val tabs = controller.tabs
        val logTab = tabs.newPanel(PanelSource.FileSource(File("pixel8.logcat"), "Google Pixel 8 Pro Android 17, API 37.1"), samples)
        tabs.newTab("pixel8.logcat", logTab)
        tabs.split(logTab, SplitOrientation.SIDE_BY_SIDE)
        val split = tabs.selected?.root as SplitNode
        (split.second as LeafNode).panel.apply {
            formatStyle = FormatStyle.COMPACT
            filterText = "level:warn"
            applyFilter()
        }
        services.notifications.post(
            "Log exported successfully (42 messages)",
            Notification.Kind.INFO,
            "Open in Logcat" to {},
            "Show in folder" to {},
        )
        shot("window", 1360, 860, host = controller) { LogcatWindow(controller) }
        shot("window-options-menu", 1360, 860, host = controller, interact = { click(1334f, 26f) }) { LogcatWindow(controller) }
        logTab.softWrap = true
        shot("window-soft-wrap", 1360, 860, host = controller) { LogcatWindow(controller) }
    }

    @Composable
    private fun toolbar(panel: LogcatPanelState, width: Int) {
        OverflowToolbar(toolbarItems(panel, null, NoopHost), Modifier.padding(8.dp).width(width.dp).height(32.dp))
    }

    private fun shot(
        name: String,
        width: Int,
        height: Int,
        host: PanelHost = NoopHost,
        interact: ImageComposeScene.() -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            IdeTheme {
                CompositionLocalProvider(LocalAppServices provides services, LocalPanelHost provides host) {
                    Box(Modifier.fillMaxSize().background(IdeColors.ToolWindowBg)) { content() }
                }
            }
        }
        try {
            scene.settle()
            scene.interact()
            scene.settle()
            val image = scene.render(System.nanoTime())
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
            File(out, "$name.png").writeBytes(png.bytes)
            println("  $name.png")
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.settle() {
        repeat(8) {
            Thread.sleep(60)
            render(System.nanoTime())
        }
    }

    private fun ImageComposeScene.click(x: Float, y: Float) {
        val at = Offset(x, y)
        sendPointerEvent(PointerEventType.Move, at)
        render(System.nanoTime())
        sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = PointerButton.Primary)
        render(System.nanoTime())
    }

    // Compose's KeyEvent factory is internal API; fine for this dev tool, which pins Compose.
    @OptIn(InternalComposeUiApi::class)
    private fun ImageComposeScene.key(key: Key, ctrl: Boolean = false) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = ctrl))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp, isCtrlPressed = ctrl))
        render(System.nanoTime())
    }

    private fun sampleMessages(): List<LogcatMessage> {
        val start = Instant.parse("2026-10-14T07:26:13.935Z")
        var t = 0L
        fun m(level: LogLevel, pid: Int, tid: Int, process: String, tag: String, text: String): LogcatMessage {
            t += 37
            return LogcatMessage(LogcatHeader(level, pid, tid, process.substringBefore(':'), process, tag, start.plusMillis(t)), text)
        }
        return buildList {
            add(m(LogLevel.INFO, 1517, 1859, "system_server", "ActivityManager", "Start proc 21903:com.example.app/u0a312 for top-activity {com.example.app/.MainActivity}"))
            add(m(LogLevel.DEBUG, 21903, 21903, "com.example.app", "MainActivity", "onCreate savedInstanceState=null"))
            add(m(LogLevel.VERBOSE, 21903, 21944, "com.example.app", "FirebaseInitProvider", "FirebaseApp initialization successful"))
            add(m(LogLevel.DEBUG, 21903, 22001, "com.example.app", "OkHttp", "--> GET https://api.example.com/v2/items?page=1"))
            add(m(LogLevel.DEBUG, 21903, 22001, "com.example.app", "OkHttp", "<-- 200 OK https://api.example.com/v2/items?page=1 (84ms, 12.4kB body)"))
            add(m(LogLevel.WARN, 21903, 16767, "com.example.app", "Bundle", "Key name expected com.google.firebase.appindexing.internal.Thing[] but value was of a different type. The default value <null> was returned."))
            add(m(LogLevel.WARN, 21903, 16767, "com.example.app", "Bundle", "Attempt to cast generated internal exception:\njava.lang.ClassCastException: Cannot cast [Ljava.lang.String; to [Landroid.os.Parcelable;\n\tat java.lang.Class.cast(Class.java:4184)\n\tat android.os.BaseBundle.getValueAt(BaseBundle.java:436)\n\tat android.os.Bundle.getParcelableArray(Bundle.java:1188)"))
            add(m(LogLevel.INFO, 793, 793, "qns", "QNS", "QNS: analytics"))
            add(m(LogLevel.DEBUG, 956, 956, "android.hardware.audio@2.0-service", "audio_hw_primary", "adev_set_parameters: enter: wireless_charging=off"))
            add(m(LogLevel.ERROR, 1267, 1399, "storaged", "storaged", "getDiskStats failed with result NOT_SUPPORTED and size 0"))
            add(m(LogLevel.WARN, 1517, 1859, "system_server", "BroadcastQueue", "Permission Denial: broadcasting Intent { act=android.intent.action.BATTERY_CHANGED flg=0x60000010 (has extras) } from null (pid=-1, uid=-1) requires com.paget96.batteryguru.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
            add(m(LogLevel.INFO, 31663, 3735, "com.google.android.gms.persistent", "AlarmManager", "setExactAndAllowWhileIdle [name: GCM_CONN_ALARM type: 2 triggerAtMillis: 116305729]"))
            add(m(LogLevel.ERROR, 8764, 8927, "com.teslacoilsw.launcher", "Amplitude", "Request failed: java.net.ConnectException: Failed to connect to api.eu.amplitude.com/127.0.0.1:443"))
            add(m(LogLevel.ERROR, 21903, 21903, "com.example.app", "AndroidRuntime", "FATAL EXCEPTION: main\nProcess: com.example.app, PID: 21903\njava.lang.IllegalStateException: Fragment not attached to a context.\n\tat androidx.fragment.app.Fragment.requireContext(Fragment.java:972)\n\tat com.example.app.ui.DetailFragment.onLoaded(DetailFragment.kt:88)"))
            add(m(LogLevel.ASSERT, 21903, 22040, "com.example.app", "libc", "Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE) in tid 22040 (RenderThread), pid 21903 (com.example.app)"))
            add(m(LogLevel.INFO, 1517, 1520, "system_server", "ActivityManager", "Process com.example.app (pid 21903) has died: fg  TOP"))
        }
    }
}
