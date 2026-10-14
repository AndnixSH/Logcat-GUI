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
package dev.logcatgui.ui.panel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.logcatgui.AppServices
import dev.logcatgui.Notification
import dev.logcatgui.adb.Device
import dev.logcatgui.adb.LogcatStream
import dev.logcatgui.adb.ProcessNameResolver
import dev.logcatgui.files.LogcatFiles
import dev.logcatgui.filter.FilterCompletion
import dev.logcatgui.filter.FilterParseException
import dev.logcatgui.filter.FilterParser
import dev.logcatgui.filter.LogcatFilter
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.platform.Shell
import dev.logcatgui.retrace.ProguardRetracer
import dev.logcatgui.settings.FormatStyle
import dev.logcatgui.settings.LogcatSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Where a panel's messages come from. */
sealed interface PanelSource {
    /** A live device. A null [serial] picks the first online device automatically. */
    data class DeviceSource(val serial: String?) : PanelSource

    /** An imported log file (read-only). */
    data class FileSource(val file: File, val description: String?) : PanelSource
}

/**
 * State of one Logcat panel (a tab, or one side of a split): its source, buffers, filter,
 * and view options. Main-thread only; streaming and parsing happen in the adb layer.
 *
 * Buffering follows Android Studio: a cycle buffer sized in KB ("Logcat cycle buffer
 * size"), trimmed in batches so the visible list isn't shifted on every message.
 */
class LogcatPanelState(
    private val services: AppServices,
    source: PanelSource,
    initialFilter: String,
    initialFormat: FormatStyle,
    initialMessages: List<LogcatMessage> = emptyList(),
) {
    var source by mutableStateOf(source)
        private set
    var filterText by mutableStateOf(initialFilter)
    var matchCase by mutableStateOf(false)
    var filterError by mutableStateOf<String?>(null)
        private set
    var softWrap by mutableStateOf(false)
    var followTail by mutableStateOf(true)
    var paused by mutableStateOf(false)
        private set
    var formatStyle by mutableStateOf(initialFormat)
    var retracer by mutableStateOf<ProguardRetracer?>(null)
        private set
    var status by mutableStateOf("")
        private set
    var totalCount by mutableIntStateOf(0)
        private set
    var pendingCount by mutableIntStateOf(0)
        private set

    /** Bumped whenever [visible] changes, so the view knows when to follow the tail. */
    var revision by mutableIntStateOf(0)
        private set

    /** Longest line among [visible] messages, in characters; sizes the horizontal scroll area. */
    var widestLine by mutableIntStateOf(0)
        private set

    /** Messages that pass the filter and ignore lists, oldest first; a subsequence of [all]. */
    val visible = mutableStateListOf<LogcatMessage>()

    private val all = ArrayDeque<LogcatMessage>()
    private var bufferChars = 0L
    private val pending = ArrayDeque<LogcatMessage>()
    private var pendingChars = 0L
    private var filter: LogcatFilter = LogcatFilter.MatchAll
    private var streamJob: Job? = null

    private val knownTags = LinkedHashSet<String>()
    private val knownPackages = LinkedHashSet<String>()
    private val knownProcesses = LinkedHashSet<String>()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            status = "Error: ${e.message ?: e.javaClass.simpleName}"
        }
    )

    private val logcatSettings: LogcatSettings get() = services.settings.settings.logcat

    val serial: String? get() = (source as? PanelSource.DeviceSource)?.serial
    val isFile: Boolean get() = source is PanelSource.FileSource

    init {
        applyFilter()
        if (initialMessages.isNotEmpty()) appendAll(initialMessages)
        when (val s = source) {
            is PanelSource.FileSource -> status = "${s.file.name}: ${initialMessages.size} messages"
            is PanelSource.DeviceSource -> if (!services.adb.isAvailable) {
                status = "adb not found. Install the Android SDK platform-tools, or set ADB_PATH or ANDROID_HOME."
            }
        }
        scope.launch { services.devices.polls.collect { onDevicesPolled() } }
        scope.launch {
            snapshotFlow { services.settings.settings.logcat }.drop(1).collect {
                rebuildVisible()
                trimToBuffer()
            }
        }
    }

    fun device(devices: List<Device> = services.devices.devices.value): Device? =
        serial?.let { s -> devices.firstOrNull { it.serial == s } }

    // ---- Source -------------------------------------------------------------------------

    fun selectDevice(device: Device) {
        if (device.serial == serial && streamJob?.isActive == true) return
        stopStream()
        source = PanelSource.DeviceSource(device.serial)
        paused = false
        clearBuffers()
        if (device.isOnline) startStream(device, resume = false) else status = "Waiting for ${device.name}…"
    }

    /** Restart Logcat (re-reads the device buffer), or reload the imported file. */
    fun restart() {
        when (val s = source) {
            is PanelSource.FileSource -> scope.launch { reload(s.file) }
            is PanelSource.DeviceSource -> {
                val d = device() ?: return
                stopStream()
                clearBuffers()
                if (d.isOnline) startStream(d, resume = false)
            }
        }
    }

    fun togglePause() {
        if (!paused) {
            paused = true
            return
        }
        paused = false
        val batch = pending.toList()
        pending.clear()
        pendingChars = 0
        pendingCount = 0
        appendAll(batch)
    }

    /** Clears the view and, for a device, its log buffer (`logcat -c`), like Android Studio. */
    fun clear() {
        clearBuffers()
        val d = device() ?: return
        if (!d.isOnline) return
        if (d.sdkMajor == 26) {
            services.notifications.post(
                "Logcat was not cleared on the device itself because of a bug in Android 8.0 (Oreo).",
                Notification.Kind.WARNING,
            )
            return
        }
        scope.launch(Dispatchers.IO) { services.adb.run("-s", d.serial, "logcat", "-c") }
    }

    /** A copy of every buffered message, for a new split of an imported file. */
    fun snapshotMessages(): List<LogcatMessage> = all.toList()

    // ---- Filter -------------------------------------------------------------------------

    /** Compiles [filterText]. On a syntax error the previous filter stays active. */
    fun applyFilter() {
        val parsed = try {
            FilterParser.parse(filterText, matchCase)
        } catch (e: FilterParseException) {
            filterError = e.message
            return
        }
        filterError = null
        if (parsed == filter) return
        filter = parsed
        rebuildVisible()
    }

    /** Records the filter in history and as the most-recently-used filter. */
    fun commitFilter() {
        if (filterError == null) services.settings.rememberFilter(filterText)
    }

    fun completionContext(): FilterCompletion.Context = FilterCompletion.Context(
        tags = knownTags.reversed(),
        packages = knownPackages.reversed(),
        processes = knownProcesses.reversed(),
        history = if (logcatSettings.filterHistoryAutocomplete) {
            services.settings.settings.let { it.favoriteFilters + it.filterHistory }.distinct()
        } else {
            emptyList()
        },
    )

    // ---- ProGuard / export --------------------------------------------------------------

    fun loadMapping(file: File) {
        scope.launch {
            status = "Loading ProGuard mapping ${file.name}…"
            try {
                retracer = ProguardRetracer.load(file.toPath())
                revision++
                status = "ProGuard mapping: ${file.name}"
                services.notifications.post("Loaded ProGuard mapping ${file.name}. Stack traces are now de-obfuscated.")
            } catch (e: Exception) {
                status = ""
                services.notifications.post("Failed to add mapping file: ${e.message}", Notification.Kind.ERROR)
            }
        }
    }

    fun clearMapping() {
        retracer = null
        revision++
    }

    /** Exports the visible messages in Android Studio's `.logcat` format. */
    fun export(file: File, openInLogcat: (File) -> Unit) {
        val messages = visible.toList()
        val device = device()
        val filterAtExport = filterText
        scope.launch {
            try {
                withContext(Dispatchers.IO) { LogcatFiles.export(file, messages, device, filterAtExport) }
                services.notifications.post(
                    "Log exported successfully (${messages.size} messages)",
                    Notification.Kind.INFO,
                    "Open in Logcat" to { openInLogcat(file) },
                    "Show in folder" to { Shell.reveal(file) },
                )
            } catch (e: IOException) {
                services.notifications.post("Could not export logs: ${e.message}", Notification.Kind.ERROR)
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    // ---- Streaming ----------------------------------------------------------------------

    private fun onDevicesPolled() {
        val s = source as? PanelSource.DeviceSource ?: return
        if (!services.adb.isAvailable) return
        val devices = services.devices.devices.value
        if (s.serial == null) {
            val first = devices.firstOrNull { it.isOnline }
            if (first != null) {
                selectDevice(first)
            } else {
                status = if (devices.isEmpty()) "No devices. Connect a device or start an emulator." else "Waiting for a device to come online…"
            }
            return
        }
        val d = devices.firstOrNull { it.serial == s.serial } ?: return
        // Reconnected, or the stream ended for another reason: resume after the last message.
        if (d.isOnline && streamJob?.isActive != true) startStream(d, resume = true)
    }

    private fun startStream(device: Device, resume: Boolean) {
        streamJob?.cancel()
        val last = pending.lastOrNull() ?: all.lastOrNull()
        val since = if (resume) last?.header?.timestamp?.plusMillis(1) else null
        val resolver = ProcessNameResolver(services.adb, device.serial)
        status = "Connecting to ${device.name}…"
        streamJob = scope.launch {
            resolver.refresh() // so the initial buffer dump already has process names
            status = "Connected to ${device.name}"
            val refresher = launch {
                while (true) {
                    delay(5_000)
                    resolver.refresh()
                }
            }
            try {
                LogcatStream(services.adb).messages(device.serial, since).collect { onMessage(it, resolver) }
            } finally {
                refresher.cancel()
            }
            status = "Disconnected from ${device.name}. Waiting for it to come back…"
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
    }

    private suspend fun reload(file: File) {
        try {
            val data = withContext(Dispatchers.IO) { LogcatFiles.import(file) }
            clearBuffers()
            appendAll(data.messages)
            status = "${file.name}: ${data.messages.size} messages"
        } catch (e: IOException) {
            services.notifications.post("Could not read ${file.name}: ${e.message}", Notification.Kind.ERROR)
        }
    }

    private fun onMessage(raw: LogcatMessage, resolver: ProcessNameResolver) {
        val name = resolver.nameFor(raw.header.pid)
        val message =
            if (name == null) raw
            else raw.copy(header = raw.header.copy(processName = name, applicationId = name.substringBefore(':')))
        if (paused) {
            pending.addLast(message)
            pendingChars += charsOf(message)
            val limit = bufferLimitChars()
            while (pendingChars > limit && pending.size > 1) pendingChars -= charsOf(pending.removeFirst())
            pendingCount = pending.size
            return
        }
        append(message)
    }

    // ---- Buffers ------------------------------------------------------------------------

    private fun append(message: LogcatMessage) {
        all.addLast(message)
        bufferChars += charsOf(message)
        learn(message)
        if (accepts(message)) {
            visible.add(message)
            revision++
            widen(message)
        }
        trimToBuffer()
        totalCount = all.size
    }

    private fun appendAll(messages: List<LogcatMessage>) {
        if (messages.isEmpty()) return
        val accepted = ArrayList<LogcatMessage>()
        for (m in messages) {
            all.addLast(m)
            bufferChars += charsOf(m)
            learn(m)
            if (accepts(m)) accepted += m
        }
        visible.addAll(accepted)
        revision++
        accepted.forEach(::widen)
        trimToBuffer()
        totalCount = all.size
    }

    /**
     * Evicts the oldest messages down to 90% of the buffer size. The batch keeps the cost of
     * shifting [visible] rare; since [visible] is an in-order subsequence of [all], the evicted
     * visible messages are exactly a prefix of it. Imported files are never trimmed.
     */
    private fun trimToBuffer() {
        if (isFile) return
        val limit = bufferLimitChars()
        if (bufferChars <= limit) return
        val target = limit - limit / 10
        var visibleEvicted = 0
        while (bufferChars > target && all.size > 1) {
            val evicted = all.removeFirst()
            bufferChars -= charsOf(evicted)
            if (visibleEvicted < visible.size && visible[visibleEvicted] === evicted) visibleEvicted++
        }
        if (visibleEvicted > 0) {
            visible.removeRange(0, visibleEvicted)
            revision++
        }
        totalCount = all.size
    }

    private fun rebuildVisible() {
        visible.clear()
        val accepted = all.filter(::accepts)
        visible.addAll(accepted)
        widestLine = accepted.maxOfOrNull(::lineWidth) ?: 0
        revision++
    }

    private fun clearBuffers() {
        all.clear()
        bufferChars = 0
        pending.clear()
        pendingChars = 0
        pendingCount = 0
        visible.clear()
        widestLine = 0
        totalCount = 0
        revision++
    }

    private fun widen(message: LogcatMessage) {
        val width = lineWidth(message)
        if (width > widestLine) widestLine = width
    }

    private fun accepts(message: LogcatMessage): Boolean {
        val s = logcatSettings
        if (s.ignoredTags.isNotEmpty() && message.header.tag in s.ignoredTags) return false
        if (s.ignoredApps.isNotEmpty() && message.header.applicationId in s.ignoredApps) return false
        return filter.matches(message)
    }

    /** Tracks recently seen values for filter completion (most recent last). */
    private fun learn(message: LogcatMessage) {
        track(knownTags, message.header.tag)
        track(knownPackages, message.header.applicationId)
        track(knownProcesses, message.header.processName)
    }

    private fun track(set: LinkedHashSet<String>, value: String) {
        if (value.isEmpty()) return
        set.remove(value)
        set.add(value)
        if (set.size > KNOWN_VALUES_LIMIT) set.remove(set.first())
    }

    private fun bufferLimitChars(): Long = logcatSettings.bufferSizeKb.toLong() * 1024

    companion object {
        private const val KNOWN_VALUES_LIMIT = 2_000

        /** Caps the horizontal scroll width; longer lines are clipped. */
        private const val MAX_LINE_WIDTH = 3_000

        /** Width of the longest line in characters, with tabs expanded as the view draws them. */
        internal fun lineWidth(m: LogcatMessage): Int {
            var widest = 0
            for (line in m.message.lineSequence()) {
                val width = line.length + 3 * line.count { it == '\t' }
                if (width > widest) widest = width
            }
            return widest.coerceAtMost(MAX_LINE_WIDTH)
        }

        /** Approximate size of a message as AS's cycle buffer counts it (text, not objects). */
        internal fun charsOf(m: LogcatMessage): Long =
            (m.message.length + m.header.tag.length + m.header.applicationId.length + 40).toLong()
    }
}
