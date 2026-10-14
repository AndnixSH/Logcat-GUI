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
package dev.logcatgui.model

/** Logcat priority, ordered from least to most severe. */
enum class LogLevel(val letter: Char, val displayName: String) {
    VERBOSE('V', "Verbose"),
    DEBUG('D', "Debug"),
    INFO('I', "Info"),
    WARN('W', "Warn"),
    ERROR('E', "Error"),
    ASSERT('A', "Assert");

    fun isAtLeast(other: LogLevel): Boolean = ordinal >= other.ordinal

    companion object {
        /** Maps a logcat priority letter. `F` (fatal) is shown as ASSERT, as in Android Studio. */
        fun fromLetter(c: Char): LogLevel? = when (c.uppercaseChar()) {
            'V' -> VERBOSE
            'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E' -> ERROR
            'A', 'F' -> ASSERT
            'S' -> VERBOSE
            else -> null
        }

        /** Accepts a single letter (`w`) or a name (`warn`, `warning`, `Error`). */
        fun parse(text: String): LogLevel? {
            val t = text.trim()
            if (t.length == 1) return fromLetter(t[0])
            val lower = t.lowercase()
            if (lower == "warning") return WARN
            if (lower == "fatal") return ASSERT
            return entries.firstOrNull { it.displayName.lowercase() == lower }
        }
    }
}
