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
package dev.logcatgui.adb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one device poller shared by every Logcat panel.
 *
 * Polls `adb devices -l` every [intervalMs], reads each new device's name and Android
 * version once with `getprop`, and remembers devices that disconnect so they stay in the
 * picker as `[OFFLINE]`, as in Android Studio. [devices] keeps discovery order.
 *
 * [scope] must dispatch on the main thread; the device map is only touched there.
 */
class DeviceService(
    private val adb: Adb,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 2_000,
) {
    private val known = LinkedHashMap<String, Device>()
    private val loadingProps = mutableSetOf<String>()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())

    /** Every device seen this session. Re-emitted on every poll, so it doubles as a heartbeat. */
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _polls = MutableStateFlow(0L)

    /** Increments after each poll; panels use it to retry streams that ended. */
    val polls: StateFlow<Long> = _polls.asStateFlow()

    fun start() {
        if (!adb.isAvailable) return
        scope.launch {
            withContext(Dispatchers.IO) { adb.startServer() }
            while (true) {
                val listed = withContext(Dispatchers.IO) { parseDevices(adb.run("devices", "-l")) }
                onPoll(listed)
                delay(intervalMs)
            }
        }
    }

    private fun onPoll(listed: List<Device>) {
        val listedSerials = listed.mapTo(HashSet()) { it.serial }
        for (entry in listed) {
            val previous = known[entry.serial]
            val updated = previous?.copy(state = entry.state, adbModel = entry.adbModel ?: previous.adbModel) ?: entry
            known[entry.serial] = updated
            if (updated.isOnline && !updated.hasProps && entry.serial !in loadingProps) loadProps(entry.serial)
        }
        for (entry in known.entries) {
            if (entry.key !in listedSerials && entry.value.state != Device.DISCONNECTED) {
                entry.setValue(entry.value.copy(state = Device.DISCONNECTED))
            }
        }
        _devices.value = known.values.toList()
        _polls.value++
    }

    private fun loadProps(serial: String) {
        loadingProps += serial
        scope.launch {
            val lines = withContext(Dispatchers.IO) { adb.run("-s", serial, "shell", PROPS_COMMAND) }
            loadingProps -= serial
            val device = known[serial] ?: return@launch
            val props = parseProps(lines) ?: return@launch // try again on a later poll
            known[serial] = device.copy(
                manufacturer = props.manufacturer,
                model = props.model,
                release = props.release,
                apiLevel = props.apiLevel,
                isEmulator = device.isEmulator || props.isQemu,
            )
            _devices.value = known.values.toList()
        }
    }

    internal data class Props(
        val manufacturer: String?,
        val model: String,
        val release: String?,
        val apiLevel: String?,
        val isQemu: Boolean,
    )

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** One shell round trip; `getprop` prints an empty line for a missing property. */
        internal const val PROPS_COMMAND =
            "getprop ro.product.manufacturer;getprop ro.product.model;getprop ro.build.version.release;" +
                "getprop ro.build.version.sdk_full;getprop ro.build.version.sdk;getprop ro.kernel.qemu;getprop ro.boot.qemu"

        internal fun parseProps(lines: List<String>): Props? {
            if (lines.size < 5) return null
            fun at(i: Int) = lines.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            val model = at(1) ?: return null
            return Props(
                manufacturer = at(0),
                model = model,
                release = at(2),
                apiLevel = at(3) ?: at(4),
                isQemu = at(5) == "1" || at(6) == "1",
            )
        }

        internal fun parseDevices(lines: List<String>): List<Device> = lines
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("List of devices") && !it.startsWith("*") }
            .mapNotNull { line ->
                val parts = line.split(WHITESPACE)
                if (parts.size < 2) return@mapNotNull null
                val model = parts.drop(2).firstOrNull { it.startsWith("model:") }?.removePrefix("model:")
                Device(serial = parts[0], state = parts[1], adbModel = model)
            }
    }
}
