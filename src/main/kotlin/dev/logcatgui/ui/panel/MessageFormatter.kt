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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.retrace.ProguardRetracer
import dev.logcatgui.settings.FormattingOptions
import dev.logcatgui.settings.NameFormat
import dev.logcatgui.settings.TimestampStyle
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.LogColors
import dev.logcatgui.ui.theme.TagPalette
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formats messages in Android Studio's column layout (Standard view):
 *
 * ```
 * 2026-10-14 00:26:13.935 21903-16767 Bundle                  com.example.app                     W  message
 * └──────────────────────────────────── header ────────────────────────────────────────────────┘  └ body ┘
 * ```
 *
 * Every column is fixed-width, so [headerWidth] can size the header exactly and the body
 * (rendered beside it) keeps soft-wrapped and multi-line text in the message column.
 */
internal object MessageFormatter {
    private const val PID_TID_WIDTH = 11
    private const val PID_WIDTH = 5

    private val DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    /**
     * Characters [header] produces for [options], badge included. A pid or tid wider than five
     * digits makes that line's header longer, as in AS, so the view uses this as a minimum.
     */
    fun headerWidth(options: FormattingOptions): Int {
        var width = 0
        if (options.timestamp.enabled) width += (if (options.timestamp.style == TimestampStyle.DATETIME) 23 else 12) + 1
        if (options.processThread.enabled) width += (if (options.processThread.includeThreadId) PID_TID_WIDTH else PID_WIDTH) + 1
        if (options.tag.enabled) width += options.tag.maxLength.coerceAtLeast(1) + 1
        if (options.appName.enabled) width += options.appName.maxLength.coerceAtLeast(1) + 1
        if (options.processName.enabled) width += options.processName.maxLength.coerceAtLeast(1) + 1
        if (options.level) width += 3
        return width
    }

    /** The header columns. [previous] is the message on the line above, for "hide repeated". */
    fun header(message: LogcatMessage, previous: LogcatMessage?, options: FormattingOptions, colors: LogColors): AnnotatedString =
        buildAnnotatedString {
            val h = message.header
            val plain = SpanStyle(color = IdeColors.EditorText)
            if (options.timestamp.enabled) {
                val formatter = if (options.timestamp.style == TimestampStyle.DATETIME) DATETIME else TIME
                withStyle(plain) { append(formatter.format(h.timestamp)) }
                append(' ')
            }
            if (options.processThread.enabled) {
                // AS's `%5d-%-5d`: the pid right-aligned, the tid left-aligned.
                val pid = h.pid.toString().padStart(PID_WIDTH)
                val ids = if (options.processThread.includeThreadId) "$pid-${h.tid.toString().padEnd(PID_WIDTH)}" else pid
                withStyle(plain) { append(ids) }
                append(' ')
            }
            if (options.tag.enabled) {
                val width = options.tag.maxLength.coerceAtLeast(1)
                val hide = options.tag.hideDuplicates && previous?.header?.tag == h.tag
                withStyle(SpanStyle(color = if (options.tag.colorize) TagPalette.colorFor(h.tag) else IdeColors.EditorText)) {
                    append(if (hide) " ".repeat(width) else fit(h.tag, width))
                }
                append(' ')
            }
            if (options.appName.enabled) {
                appendName(h.applicationId.ifEmpty { "pid-${h.pid}" }, previous?.header?.applicationId == h.applicationId, options.appName, plain)
            }
            if (options.processName.enabled) {
                appendName(h.processName.ifEmpty { "pid-${h.pid}" }, previous?.header?.processName == h.processName, options.processName, plain)
            }
            if (options.level) {
                withStyle(SpanStyle(color = colors.indicatorForeground(h.logLevel), background = colors.indicatorBackground(h.logLevel))) {
                    // Non-breaking spaces so the badge padding is never dropped as trailing whitespace.
                    append(" ${h.logLevel.letter} ")
                }
            }
        }

    private fun AnnotatedString.Builder.appendName(value: String, sameAsPrevious: Boolean, format: NameFormat, style: SpanStyle) {
        val width = format.maxLength.coerceAtLeast(1)
        withStyle(style) { append(if (format.hideDuplicates && sameAsPrevious) " ".repeat(width) else fit(value, width)) }
        append(' ')
    }

    /**
     * The message in its level color, de-obfuscated when a ProGuard mapping is loaded. Tabs are
     * expanded because the text renderer has no tab stops and draws `\t` as a missing glyph.
     */
    fun body(message: LogcatMessage, retracer: ProguardRetracer?, colors: LogColors): AnnotatedString {
        val text = retracer?.retrace(message.message) ?: message.message
        return AnnotatedString(text.replace("\t", "    "), SpanStyle(color = colors.message(message.header.logLevel)))
    }

    /** Pads to [width], or truncates with an ellipsis so the columns stay aligned. */
    fun fit(text: String, width: Int): String =
        if (text.length <= width) text.padEnd(width) else text.take(width - 1) + "…"
}
