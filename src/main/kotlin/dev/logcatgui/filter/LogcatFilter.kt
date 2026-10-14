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
package dev.logcatgui.filter

import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogLevel.ERROR
import dev.logcatgui.model.LogcatMessage

/** A compiled filter query. */
sealed interface LogcatFilter {
    fun matches(message: LogcatMessage): Boolean

    data object MatchAll : LogcatFilter {
        override fun matches(message: LogcatMessage) = true
    }

    data class And(val filters: List<LogcatFilter>) : LogcatFilter {
        override fun matches(message: LogcatMessage) = filters.all { it.matches(message) }
    }

    data class Or(val filters: List<LogcatFilter>) : LogcatFilter {
        override fun matches(message: LogcatMessage) = filters.any { it.matches(message) }
    }

    data class Not(val filter: LogcatFilter) : LogcatFilter {
        override fun matches(message: LogcatMessage) = !filter.matches(message)
    }

    /** `key:value`, `key=:value` or `key~:regex` on a text field of the message. */
    data class FieldMatch(val field: Field, val matcher: TextMatcher) : LogcatFilter {
        override fun matches(message: LogcatMessage) = when (field) {
            Field.MESSAGE -> matcher.matches(message.message)
            Field.TAG -> matcher.matches(message.header.tag)
            Field.PACKAGE -> matcher.matches(message.header.applicationId)
            Field.PROCESS -> matcher.matches(message.header.processName)
            Field.LINE -> matcher.matches(message.header.tag) || matcher.matches(message.message)
        }
    }

    /**
     * Standalone approximation of AS's `package:mine`: there is no open project, so this
     * matches any process whose name looks like an app package.
     */
    data object PackageMine : LogcatFilter {
        override fun matches(message: LogcatMessage) = '.' in message.header.applicationId
    }

    data class LevelAtLeast(val level: LogLevel) : LogcatFilter {
        override fun matches(message: LogcatMessage) = message.header.logLevel.isAtLeast(level)
    }

    data class PidEquals(val pid: Int) : LogcatFilter {
        override fun matches(message: LogcatMessage) = message.header.pid == pid
    }

    data class TidEquals(val tid: Int) : LogcatFilter {
        override fun matches(message: LogcatMessage) = message.header.tid == tid
    }

    /** `age:5m` — messages logged within the last [millis], measured against [Clock]. */
    data class AgeWithin(val millis: Long) : LogcatFilter {
        override fun matches(message: LogcatMessage) =
            message.header.timestamp.toEpochMilli() >= Clock.nowMillis() - millis
    }

    /** `is:crash` — a JVM crash (`FATAL EXCEPTION`) or a native crash report. */
    data object IsCrash : LogcatFilter {
        override fun matches(message: LogcatMessage): Boolean {
            val header = message.header
            if (!header.logLevel.isAtLeast(ERROR)) return false
            return (header.tag == "AndroidRuntime" && message.message.startsWith("FATAL EXCEPTION")) ||
                (header.tag in NATIVE_CRASH_TAGS && NATIVE_CRASH_MARKERS.any { it in message.message })
        }

        private val NATIVE_CRASH_TAGS = setOf("DEBUG", "libc", "crash_dump64", "crash_dump32")
        private val NATIVE_CRASH_MARKERS = listOf("*** *** ***", "Fatal signal", "backtrace:")
    }

    /** `is:stacktrace` — the message contains at least one Java stack frame. */
    data object IsStacktrace : LogcatFilter {
        private val FRAME = Regex("""(?m)^\s*at \S+\(.*\)\s*$""")
        override fun matches(message: LogcatMessage) = FRAME.containsMatchIn(message.message)
    }

    enum class Field { MESSAGE, TAG, PACKAGE, PROCESS, LINE }

    /** Replaceable time source so `age:` is testable. */
    object Clock {
        @Volatile
        var nowMillis: () -> Long = System::currentTimeMillis
    }
}

/** How a `key:value` term compares text. Equality is by definition, so filters can be compared. */
sealed interface TextMatcher {
    fun matches(text: String): Boolean

    data class Contains(val value: String, val ignoreCase: Boolean = true) : TextMatcher {
        override fun matches(text: String) = text.contains(value, ignoreCase)
    }

    data class Exact(val value: String, val ignoreCase: Boolean = true) : TextMatcher {
        override fun matches(text: String) = text.equals(value, ignoreCase)
    }

    data class Matches(val pattern: String, val ignoreCase: Boolean = true) : TextMatcher {
        private val regex = Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        override fun matches(text: String) = regex.containsMatchIn(text)
    }
}
