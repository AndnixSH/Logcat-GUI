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
package dev.logcatgui.settings

import dev.logcatgui.model.LogLevel
import kotlinx.serialization.Serializable

/** Everything persisted between runs. */
@Serializable
data class AppSettings(
    val logcat: LogcatSettings = LogcatSettings(),
    val format: FormatSettings = FormatSettings(),
    val colors: LogcatColorScheme = LogcatColorScheme(),
    val filterHistory: List<String> = emptyList(),
    val favoriteFilters: List<String> = emptyList(),
    /** For "Use most-recently-used filter as the default". */
    val lastFilter: String? = null,
    val lastDirectory: String? = null,
)

/**
 * Android Studio's Settings → Tools → Logcat (`AndroidLogcatSettings`).
 *
 * Defaults match AS, except [defaultFilter]: AS uses `package:mine`, which only means
 * something with an open project, so a standalone viewer starts unfiltered.
 */
@Serializable
data class LogcatSettings(
    /** "Logcat cycle buffer size". AS stores bytes; the dialog edits KB. */
    val bufferSizeKb: Int = DEFAULT_BUFFER_KB,
    /** "Default filter for new Logcat windows". */
    val defaultFilter: String = "",
    /** "Use most-recently-used filter as the default". */
    val mostRecentlyUsedFilterIsDefault: Boolean = false,
    /** "Add filters from history to autocomplete". */
    val filterHistoryAutocomplete: Boolean = false,
    /** "Ignore tags" — messages with these tags are never shown. */
    val ignoredTags: Set<String> = emptySet(),
    /** "Ignore apps" — messages from these packages are never shown. */
    val ignoredApps: Set<String> = emptySet(),
    /** "Override font size". */
    val overrideFontSize: Boolean = false,
    val fontSize: Int = DEFAULT_FONT_SIZE,
) {
    companion object {
        const val DEFAULT_BUFFER_KB = 1024
        const val MAX_BUFFER_KB = 100 * 1024
        /** Above this, the dialog shows AS's "large buffer size" warning. */
        const val LARGE_BUFFER_KB = 10 * 1024
        const val DEFAULT_FONT_SIZE = 13
        const val MAX_HISTORY = 20

        fun isValidBufferSize(kb: Int) = kb in 1..MAX_BUFFER_KB
    }
}

/**
 * Android Studio's Editor → Color Scheme → Android Logcat. Colors are `RRGGBB` hex strings;
 * defaults are `LogcatColorSchemeDarcula.xml`, which the "Dark" scheme inherits.
 */
@Serializable
data class LogcatColorScheme(
    val levels: Map<LogLevel, LevelColors> = DEFAULT_LEVELS,
) {
    fun forLevel(level: LogLevel): LevelColors = levels[level] ?: DEFAULT_LEVELS.getValue(level)

    companion object {
        val DEFAULT_LEVELS: Map<LogLevel, LevelColors> = mapOf(
            LogLevel.VERBOSE to LevelColors(indicatorForeground = "000000", indicatorBackground = "D6D6D6", message = "BBBBBB"),
            LogLevel.DEBUG to LevelColors(indicatorForeground = "BBBBBB", indicatorBackground = "305D78", message = "299999"),
            LogLevel.INFO to LevelColors(indicatorForeground = "E9F5E6", indicatorBackground = "6A8759", message = "ABC023"),
            LogLevel.WARN to LevelColors(indicatorForeground = "000000", indicatorBackground = "BBB529", message = "BBB529"),
            LogLevel.ERROR to LevelColors(indicatorForeground = "000000", indicatorBackground = "CF5B56", message = "FF6B68"),
            LogLevel.ASSERT to LevelColors(indicatorForeground = "FFFFFF", indicatorBackground = "8B3C3C", message = "FF6B68"),
        )
    }
}

@Serializable
data class LevelColors(
    val indicatorForeground: String,
    val indicatorBackground: String,
    val message: String,
)
