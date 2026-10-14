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

/**
 * A device seen this session. Labels follow Android Studio's device picker:
 *
 * ```
 * samsung SM-X710 (adb-R52Y…_adb-tls-connect._tcp)   Android 16, API 36.1
 * OPPO CPH1823                                        Android 12, API 32 [OFFLINE]
 * ```
 */
data class Device(
    val serial: String,
    /** The `adb devices` state (`device`, `offline`, `unauthorized`, …), or [DISCONNECTED]. */
    val state: String,
    /** `model:` from `adb devices -l` (underscores for spaces); a fallback until props load. */
    val adbModel: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val release: String? = null,
    /** `ro.build.version.sdk_full` (e.g. `36.1`), falling back to `ro.build.version.sdk`. */
    val apiLevel: String? = null,
    val isEmulator: Boolean = serial.startsWith("emulator-"),
) {
    val isOnline: Boolean get() = state == ONLINE
    val hasProps: Boolean get() = model != null

    /** "samsung SM-X710", or the best fallback before properties are known. */
    val name: String
        get() = when {
            model != null -> listOfNotNull(manufacturer, model).joinToString(" ")
            adbModel != null -> adbModel.replace('_', ' ')
            else -> serial
        }

    /** The main label: the name, plus the serial while the device is connected. */
    val label: String get() = if (isOnline && name != serial) "$name ($serial)" else name

    /** "Android 16, API 36.1" (empty until properties are known). */
    val details: String
        get() = listOfNotNull(release?.let { "Android $it" }, apiLevel?.let { "API $it" }).joinToString(", ")

    /** "[OFFLINE]" / "[UNAUTHORIZED]" for devices that can't stream, else null. */
    val statusTag: String?
        get() = when (state) {
            ONLINE -> null
            DISCONNECTED, "offline" -> "[OFFLINE]"
            else -> "[${state.uppercase()}]"
        }

    val sdkMajor: Int? get() = apiLevel?.substringBefore('.')?.toIntOrNull()

    companion object {
        const val ONLINE = "device"

        /** Seen earlier this session but no longer listed by adb. */
        const val DISCONNECTED = "disconnected"
    }
}
