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
package dev.logcatgui.ui.panel

import androidx.compose.ui.graphics.Color
import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogcatHeader
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.settings.FormattingOptions
import dev.logcatgui.settings.TimestampFormat
import dev.logcatgui.settings.TimestampStyle
import dev.logcatgui.ui.theme.LogColors
import dev.logcatgui.ui.theme.TagPalette
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageFormatterTest {
    private val colors = LogColors.Default

    private fun msg(message: String = "m", tag: String = "Tag", app: String = "com.example", pid: Int = 1234, tid: Int = 1250) =
        LogcatMessage(LogcatHeader(LogLevel.INFO, pid, tid, app, app, tag, Instant.EPOCH), message)

    @Test
    fun fitPadsAndTruncates() {
        assertEquals("ab   ", MessageFormatter.fit("ab", 5))
        assertEquals("abcd…", MessageFormatter.fit("abcdefgh", 5))
        assertEquals("abcde", MessageFormatter.fit("abcde", 5))
    }

    @Test
    fun headerWidthMatchesTheTextForEveryPreset() {
        val presets = listOf(
            FormattingOptions.STANDARD,
            FormattingOptions.COMPACT,
            FormattingOptions.STANDARD.copy(processName = FormattingOptions.STANDARD.processName.copy(enabled = true)),
            FormattingOptions.STANDARD.copy(level = false, timestamp = TimestampFormat(TimestampStyle.TIME)),
        )
        for (options in presets) {
            for (m in listOf(msg(), msg(tag = "AVeryLongTagNameThatExceedsTheColumn", app = "com.example.a.very.long.package.name.that.overflows", pid = 7, tid = 8))) {
                assertEquals(MessageFormatter.headerWidth(options), MessageFormatter.header(m, null, options, colors).text.length, "$options")
            }
        }
    }

    @Test
    fun compactViewShowsOnlyTimeAndLevel() {
        val text = MessageFormatter.header(msg(), null, FormattingOptions.COMPACT, colors).text
        assertEquals(12 + 1 + 3, text.length)
        assertFalse("Tag" in text)
        assertTrue(text.endsWith(" I "))
    }

    @Test
    fun unresolvedPackageShowsPid() {
        val text = MessageFormatter.header(msg(app = ""), null, FormattingOptions.STANDARD, colors).text
        assertTrue("pid-1234" in text, text)
    }

    @Test
    fun hidesRepeatedTagsWhenAsked() {
        val options = FormattingOptions.STANDARD.copy(tag = FormattingOptions.STANDARD.tag.copy(hideDuplicates = true))
        val first = msg(tag = "OkHttp")
        val second = msg(tag = "OkHttp", message = "again")
        assertTrue("OkHttp" in MessageFormatter.header(second, null, options, colors).text)
        assertFalse("OkHttp" in MessageFormatter.header(second, first, options, colors).text)
    }

    @Test
    fun bodyKeepsLineBreaksAndExpandsTabs() {
        assertEquals("first\n    at Foo.bar", MessageFormatter.body(msg("first\n\tat Foo.bar"), null, colors).text)
    }

    @Test
    fun tagColorsFollowAndroidStudiosPalette() {
        // "Bundle" is drawn in #D28546 (Orange) in Android Studio — see the user's screenshot.
        assertEquals(Color(0xFFD28546), TagPalette.colorFor("Bundle"))
        assertEquals(TagPalette.colorFor("OkHttp"), TagPalette.colorFor("OkHttp"))
    }
}
