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
package dev.logcatgui.parser

import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogcatHeader
import dev.logcatgui.model.LogcatMessage
import java.time.Instant

/**
 * Incremental parser for `adb logcat -v long -v epoch` output.
 *
 * ```
 * [ 1760342096.123  1234: 1250 I/ActivityManager ]
 * first message line
 * second message line
 *
 * ```
 *
 * Feed it one line at a time with [process]; it returns a message once that message is
 * complete (at its terminating blank line, or when the next header arrives). Call [flush]
 * at end of stream to get any message still pending. Not thread-safe.
 */
class LogcatLongParser {
    private var pendingHeader: LogcatHeader? = null
    private val body = StringBuilder()

    fun process(line: String): LogcatMessage? {
        val header = parseHeader(line)
        if (header != null) {
            val completed = emitPending()
            pendingHeader = header
            return completed
        }
        if (line.isBlank()) {
            return emitPending()
        }
        if (pendingHeader != null) {
            if (body.isNotEmpty()) body.append('\n')
            body.append(line)
        }
        return null
    }

    fun flush(): LogcatMessage? = emitPending()

    private fun emitPending(): LogcatMessage? {
        val header = pendingHeader ?: return null
        val message = LogcatMessage(header, body.toString())
        pendingHeader = null
        body.setLength(0)
        return message
    }

    companion object {
        // [ <epoch.frac>  <pid>:<tid> <L>/<tag> ]   (pid/tid are space-padded)
        private val HEADER = Regex("""^\[\s+(\d+)\.(\d+)\s+(\d+):\s*(\d+)\s+([VDIWEAFS])/(.*?)\s*]$""")

        internal fun parseHeader(line: String): LogcatHeader? {
            val m = HEADER.matchEntire(line) ?: return null
            val (secs, frac, pid, tid, level, tag) = m.destructured
            return LogcatHeader(
                logLevel = LogLevel.fromLetter(level[0]) ?: LogLevel.VERBOSE,
                pid = pid.toInt(),
                tid = tid.toInt(),
                applicationId = "",
                processName = "",
                tag = tag,
                timestamp = Instant.ofEpochMilli(secs.toLong() * 1000 + fractionToMillis(frac)),
            )
        }

        /** "1" -> 100, "12" -> 120, "123" -> 123, "123456" -> 123. */
        private fun fractionToMillis(frac: String): Long =
            frac.padEnd(3, '0').substring(0, 3).toLong()
    }
}
