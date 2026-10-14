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
import java.io.IOException

/** Takes a device screenshot with `adb exec-out screencap -p`. */
object ScreenCapture {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    /** Returns PNG bytes, or throws [IOException] with a readable reason. */
    suspend fun capture(adb: Adb, serial: String): ByteArray = withContext(Dispatchers.IO) {
        val bytes = adb.runBytes("-s", serial, "exec-out", "screencap", "-p")
        if (bytes.size < PNG_SIGNATURE.size || !bytes.copyOf(PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            val text = bytes.decodeToString().trim().take(200)
            throw IOException(if (text.isEmpty()) "The device returned no image." else text)
        }
        bytes
    }
}
