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

import kotlinx.serialization.Serializable

/**
 * Which header columns a Logcat view shows, mirroring Android Studio's "Logcat Format"
 * (Modify Views) options. The presets use the same values as AS's
 * `AndroidLogcatFormattingOptions.DEFAULT_STANDARD` / `DEFAULT_COMPACT`.
 */
@Serializable
data class FormattingOptions(
    val timestamp: TimestampFormat = TimestampFormat(),
    val processThread: ProcessThreadFormat = ProcessThreadFormat(),
    val tag: TagFormat = TagFormat(),
    val appName: NameFormat = NameFormat(enabled = true),
    val processName: NameFormat = NameFormat(enabled = false),
    val level: Boolean = true,
) {
    companion object {
        val STANDARD = FormattingOptions()
        val COMPACT = FormattingOptions(
            timestamp = TimestampFormat(TimestampStyle.TIME, enabled = true),
            processThread = ProcessThreadFormat(enabled = false),
            tag = TagFormat(enabled = false),
            appName = NameFormat(enabled = false),
            processName = NameFormat(enabled = false),
        )
    }
}

@Serializable
enum class TimestampStyle { DATETIME, TIME }

@Serializable
data class TimestampFormat(val style: TimestampStyle = TimestampStyle.DATETIME, val enabled: Boolean = true)

@Serializable
data class ProcessThreadFormat(val includeThreadId: Boolean = true, val enabled: Boolean = true)

@Serializable
data class TagFormat(
    val maxLength: Int = 23,
    val hideDuplicates: Boolean = false,
    val enabled: Boolean = true,
    val colorize: Boolean = true,
)

@Serializable
data class NameFormat(val maxLength: Int = 35, val hideDuplicates: Boolean = false, val enabled: Boolean = true)

/** The two named views of the toolbar's format menu. */
@Serializable
enum class FormatStyle(val displayName: String) {
    STANDARD("Standard"),
    COMPACT("Compact"),
}

@Serializable
data class FormatSettings(
    val standard: FormattingOptions = FormattingOptions.STANDARD,
    val compact: FormattingOptions = FormattingOptions.COMPACT,
    /** "Use as default view for new windows". */
    val defaultStyle: FormatStyle = FormatStyle.STANDARD,
) {
    fun options(style: FormatStyle) = when (style) {
        FormatStyle.STANDARD -> standard
        FormatStyle.COMPACT -> compact
    }

    fun with(style: FormatStyle, options: FormattingOptions) = when (style) {
        FormatStyle.STANDARD -> copy(standard = options)
        FormatStyle.COMPACT -> copy(compact = options)
    }
}
