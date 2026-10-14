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
package dev.logcatgui.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.logcatgui.model.LogLevel
import dev.logcatgui.settings.LogcatColorScheme

/** The Logcat color scheme in effect, resolved from settings to Compose colors. */
@Immutable
class LogColors(scheme: LogcatColorScheme) {
    private val indicatorFg = LogLevel.entries.associateWith { hex(scheme.forLevel(it).indicatorForeground) }
    private val indicatorBg = LogLevel.entries.associateWith { hex(scheme.forLevel(it).indicatorBackground) }
    private val message = LogLevel.entries.associateWith { hex(scheme.forLevel(it).message) }

    fun indicatorForeground(level: LogLevel): Color = indicatorFg.getValue(level)
    fun indicatorBackground(level: LogLevel): Color = indicatorBg.getValue(level)
    fun message(level: LogLevel): Color = message.getValue(level)

    companion object {
        val Default = LogColors(LogcatColorScheme())

        /** Parses `RRGGBB` (or `AARRGGBB`); null if malformed. */
        fun parseHex(text: String): Color? {
            val t = text.trim().removePrefix("#")
            val v = t.toLongOrNull(16) ?: return null
            return when (t.length) {
                6 -> Color(0xFF000000 or v)
                8 -> Color(v)
                else -> null
            }
        }

        private fun hex(text: String) = parseHex(text) ?: Color.Magenta
    }
}

val LocalLogColors = staticCompositionLocalOf { LogColors.Default }

/**
 * Android Studio's tag colors: the dark variant of `palette/logcat-tags-palette.json`
 * (16 colors × 5 tones), indexed the way AS's `ColorPaletteManager` does it:
 * `i = |hash| % (16 × 5)`, color = `i % 16`, tone = `i / 16 % 5`.
 */
object TagPalette {
    private val COLORS: List<List<Long>> = listOf(
        listOf(0x929292, 0xa3a3a3, 0xb5b5b5, 0xc7c7c7, 0xd9d9d9), // Gray
        listOf(0xA9B7C6, 0xb9c4d0, 0xc8d1da, 0xd7dee4, 0xe7eaef), // Light Blue
        listOf(0xD28546, 0xd7935b, 0xdca06f, 0xe1ae84, 0xe6bb98), // Orange
        listOf(0xA8C023, 0xc0d930, 0xc9df4e, 0xd2e46c, 0xdbea8b), // Green
        listOf(0xAE8ABE, 0xb999c7, 0xc4a9d0, 0xd0bad9, 0xdbcae2), // Light Purple
        listOf(0xD27981, 0xd6858b, 0xda9096, 0xde9ca1, 0xe2a7ac), // Pink
        listOf(0x3AA192, 0x41b4a3, 0x4fbfaf, 0x62c6b7, 0x74cdc0), // Turquoise
        listOf(0xFFC66D, 0xffbb4d, 0xffaf2e, 0xffa30f, 0xf09400), // Tan
        listOf(0x029CDE, 0x02aef7, 0x17b8fd, 0x30bffd, 0x49c7fd), // Pacific Blue
        listOf(0x19A492, 0x1bb19d, 0x1dbfa9, 0x1eccb5, 0x20d9c1), // Teal
        listOf(0xA388B4, 0xab91ba, 0xb29bc0, 0xb9a4c6, 0xc0adcc), // Purple
        listOf(0xB78A57, 0xbd9465, 0xc39e74, 0xc9a882, 0xd0b290), // Dark Orange
        listOf(0x7292CA, 0x819ecf, 0x90a9d5, 0x9fb5db, 0xaec0e0), // Indigo
        listOf(0xBBB529, 0xb4af27, 0xaba726, 0xa39f24, 0x9b9722), // Lime
        listOf(0xD36FB1, 0xd77eba, 0xdc8ec2, 0xe19dcb, 0xe6add3), // Magenta
        listOf(0x4291FF, 0x579dff, 0x6ba9ff, 0x80b5ff, 0x94c1ff), // Vivid Blue
    )
    private const val TONES = 5
    private val cache = HashMap<String, Color>()

    fun colorFor(tag: String): Color = cache.getOrPut(tag) {
        val i = Math.floorMod(Math.abs(tag.hashCode()), COLORS.size * TONES)
        Color(0xFF000000 or COLORS[i % COLORS.size][i / COLORS.size % TONES])
    }
}
