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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Records the device screen with the on-device `screenrecord` tool.
 *
 * The recording is written to [REMOTE_PATH]; [stop] sends SIGINT so `screenrecord`
 * finalizes the MP4 (killing the adb client instead would leave a truncated file), and
 * [pullTo] copies it to the computer and deletes it from the device.
 */
class ScreenRecorder(private val adb: Adb, private val serial: String) {
    private var process: Process? = null
    private var previousShowTouches: String? = null

    /** False once the recording stops, including when `screenrecord` hits its time limit. */
    val isRecording: Boolean get() = process?.isAlive == true

    suspend fun start(bitRateMbps: Int, showTaps: Boolean) = withContext(Dispatchers.IO) {
        check(process == null) { "Already started" }
        if (showTaps) {
            previousShowTouches = adb.run("-s", serial, "shell", "settings", "get", "system", "show_touches").firstOrNull()?.trim()
            adb.run("-s", serial, "shell", "settings", "put", "system", "show_touches", "1")
        }
        adb.run("-s", serial, "shell", "rm", "-f", REMOTE_PATH)
        process = adb.startStreaming(
            "-s", serial, "shell", "screenrecord", "--bit-rate", (bitRateMbps * 1_000_000).toString(), REMOTE_PATH,
        )
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val p = process ?: return@withContext
        if (p.isAlive) {
            // pkill is in toybox from Android 6; fall back to kill + pidof on older shells.
            adb.run("-s", serial, "shell", "pkill -INT screenrecord || kill -2 \$(pidof screenrecord)")
            if (!p.waitFor(15, TimeUnit.SECONDS)) p.destroy()
        }
        previousShowTouches?.let { previous ->
            val value = previous.toIntOrNull()?.toString() ?: "0"
            adb.run("-s", serial, "shell", "settings", "put", "system", "show_touches", value)
            previousShowTouches = null
        }
    }

    /** Copies the recording to [destination] and removes it from the device. */
    suspend fun pullTo(destination: File) = withContext(Dispatchers.IO) {
        val output = adb.run("-s", serial, "pull", REMOTE_PATH, destination.absolutePath, timeoutMs = 120_000)
        if (!destination.isFile || destination.length() == 0L) {
            throw IOException(output.lastOrNull { it.isNotBlank() } ?: "Could not copy the recording from the device.")
        }
        adb.run("-s", serial, "shell", "rm", "-f", REMOTE_PATH)
    }

    /** Deletes the recording from the device without copying it. */
    suspend fun discard() = withContext(Dispatchers.IO) {
        adb.run("-s", serial, "shell", "rm", "-f", REMOTE_PATH)
    }

    companion object {
        const val REMOTE_PATH = "/sdcard/logcat-gui-screen-recording.mp4"
    }
}
