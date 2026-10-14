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

import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.parser.LogcatLongParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

/** Streams parsed logcat messages from one device. */
class LogcatStream(private val adb: Adb) {

    /**
     * Emits messages from `adb logcat -v long -v epoch` until the process exits (for example
     * when the device disconnects), then completes. Cancelling collection kills the process.
     *
     * With [since] set, only messages at or after that time are requested (`-T`). This is
     * used when reconnecting so the device's buffer is not replayed as duplicates.
     */
    fun messages(serial: String, since: Instant? = null): Flow<LogcatMessage> = callbackFlow {
        val args = buildList {
            addAll(listOf("-s", serial, "logcat", "-v", "long", "-v", "epoch"))
            if (since != null) {
                add("-T")
                add("${since.epochSecond}.${(since.nano / 1_000_000).toString().padStart(3, '0')}")
            }
        }
        val process = adb.startStreaming(*args.toTypedArray())

        val reader = launch {
            val parser = LogcatLongParser()
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        parser.process(line)?.let { send(it) }
                    }
                }
                parser.flush()?.let { send(it) }
            } catch (_: IOException) {
                // The stream was closed because we destroyed the process.
            }
            channel.close()
        }

        awaitClose {
            reader.cancel()
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
