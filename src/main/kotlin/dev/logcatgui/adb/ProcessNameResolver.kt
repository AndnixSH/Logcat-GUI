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

/**
 * Maps pids to process names using the device's `ps`. For app processes the name is the
 * package name (or `package:suffix` for secondary processes).
 *
 * Standalone counterpart of Android Studio's `ProcessNameMonitor`, which gets the same data
 * from ddmlib client tracking.
 */
class ProcessNameResolver(private val adb: Adb, private val serial: String) {

    @Volatile
    private var names: Map<Int, String> = emptyMap()

    fun nameFor(pid: Int): String? = names[pid]

    suspend fun refresh() = withContext(Dispatchers.IO) {
        // Modern toybox ps; fall back to plain `ps` on old (pre-O) devices.
        val parsed = parsePs(adb.run("-s", serial, "shell", "ps", "-A", "-o", "PID,NAME"))
            .ifEmpty { parsePs(adb.run("-s", serial, "shell", "ps")) }
        if (parsed.isNotEmpty()) names = parsed
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /**
         * Accepts both `PID NAME` output and full `USER PID PPID ... NAME` output: the pid is
         * the first numeric column of the first two, and the name is the last column.
         */
        internal fun parsePs(lines: List<String>): Map<Int, String> = buildMap {
            for (line in lines) {
                val parts = line.trim().split(WHITESPACE)
                if (parts.size < 2) continue
                val pid = parts[0].toIntOrNull() ?: parts[1].toIntOrNull() ?: continue
                put(pid, parts.last())
            }
        }
    }
}
