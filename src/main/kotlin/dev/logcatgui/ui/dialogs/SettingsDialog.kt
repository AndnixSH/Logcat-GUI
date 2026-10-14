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
package dev.logcatgui.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.logcatgui.LocalAppServices
import dev.logcatgui.model.LogLevel
import dev.logcatgui.settings.AppSettings
import dev.logcatgui.settings.FormatSettings
import dev.logcatgui.settings.FormatStyle
import dev.logcatgui.settings.FormattingOptions
import dev.logcatgui.settings.LevelColors
import dev.logcatgui.settings.LogcatColorScheme
import dev.logcatgui.settings.LogcatSettings
import dev.logcatgui.settings.TimestampStyle
import dev.logcatgui.ui.components.HorizontalLine
import dev.logcatgui.ui.components.IdeButton
import dev.logcatgui.ui.components.IdeCheckbox
import dev.logcatgui.ui.components.IdeDropdown
import dev.logcatgui.ui.components.IdeRadioButton
import dev.logcatgui.ui.components.IdeTextField
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.LogColors
import dev.logcatgui.ui.window.SettingsPage

/**
 * Android Studio's Logcat settings in one dialog:
 * - **Logcat** — Settings → Tools → Logcat;
 * - **Logcat Format** — the toolbar's "Modify Views" (Standard / Compact presets);
 * - **Color Scheme** — Editor → Color Scheme → Android Logcat.
 *
 * Edits go to a draft; OK/Apply write it to the settings file.
 */
@Composable
fun SettingsDialog(initialPage: SettingsPage, onClose: () -> Unit) {
    IdeDialog("Settings", onCloseRequest = onClose, size = DpSize(900.dp, 660.dp)) {
        SettingsContent(initialPage, onClose)
    }
}

@Composable
internal fun SettingsContent(initialPage: SettingsPage, onClose: () -> Unit) {
    val store = LocalAppServices.current.settings
    var draft by remember { mutableStateOf(store.settings) }
    var page by remember { mutableStateOf(initialPage) }
    var bufferText by remember { mutableStateOf(draft.logcat.bufferSizeKb.toString()) }
    var fontText by remember { mutableStateOf(draft.logcat.fontSize.toString()) }
    val bufferValid = bufferText.trim().toIntOrNull()?.let(LogcatSettings::isValidBufferSize) == true
    val fontValid = !draft.logcat.overrideFontSize || fontText.trim().toIntOrNull() in FONT_RANGE
    val valid = bufferValid && fontValid

    fun commit(): AppSettings = draft.let { d ->
        d.copy(
            logcat = d.logcat.copy(
                bufferSizeKb = bufferText.trim().toIntOrNull() ?: d.logcat.bufferSizeKb,
                fontSize = fontText.trim().toIntOrNull()?.coerceIn(FONT_RANGE) ?: d.logcat.fontSize,
            ),
        )
    }

    fun apply() {
        val committed = commit()
        draft = committed
        // Only the edited sections: history and directories may have changed meanwhile.
        store.update { it.copy(logcat = committed.logcat, format = committed.format, colors = committed.colors) }
    }

    val dirty = commit().let { c -> c.logcat != store.settings.logcat || c.format != store.settings.format || c.colors != store.settings.colors }

    Column(Modifier.fillMaxSize().background(IdeColors.DialogBg)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.width(210.dp).fillMaxHeight().background(IdeColors.ToolWindowBgAlt).padding(vertical = 8.dp)) {
                SettingsPage.entries.forEach { p ->
                    NavRow(p.title, selected = page == p) { page = p }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(IdeColors.PanelBorder))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
                Text(page.title, style = IdeText.Bold.copy(fontSize = IdeText.Default.fontSize * 1.15f))
                Spacer(Modifier.height(14.dp))
                when (page) {
                    SettingsPage.LOGCAT -> LogcatPage(
                        settings = draft.logcat,
                        bufferText = bufferText,
                        onBufferText = { bufferText = it },
                        fontText = fontText,
                        onFontText = { fontText = it },
                        onChange = { draft = draft.copy(logcat = it) },
                    )
                    SettingsPage.FORMAT -> FormatPage(draft.format, LogColors(draft.colors)) { draft = draft.copy(format = it) }
                    SettingsPage.COLORS -> ColorSchemePage(draft.colors, draft.format.options(draft.format.defaultStyle)) {
                        draft = draft.copy(colors = it)
                    }
                }
            }
        }
        HorizontalLine(color = IdeColors.PanelBorder)
        DialogButtons {
            IdeButton("Cancel", onClose)
            IdeButton("Apply", ::apply, enabled = valid && dirty)
            IdeButton("OK", { if (valid) { apply(); onClose() } }, default = true, enabled = valid)
        }
    }
}

private val FONT_RANGE = 6..40

@Composable
private fun NavRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .height(26.dp)
            .background(if (selected) IdeColors.SelectionBg else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = IdeText.Default)
    }
}

// ---- Logcat (Settings → Tools → Logcat) -------------------------------------------------

@Composable
private fun LogcatPage(
    settings: LogcatSettings,
    bufferText: String,
    onBufferText: (String) -> Unit,
    fontText: String,
    onFontText: (String) -> Unit,
    onChange: (LogcatSettings) -> Unit,
) {
    var ignoredTagsText by remember { mutableStateOf(settings.ignoredTags.joinToString(" ")) }
    var ignoredAppsText by remember { mutableStateOf(settings.ignoredApps.joinToString(" ")) }
    val buffer = bufferText.trim().toIntOrNull()
    val bufferValid = buffer != null && LogcatSettings.isValidBufferSize(buffer)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledRow("Logcat cycle buffer size:") {
            IdeTextField(bufferText, onBufferText, Modifier.width(110.dp), error = !bufferValid)
            Text(" KB", style = IdeText.Default)
        }
        when {
            !bufferValid -> Note(
                "Invalid. Please enter an integer between 1 and ${LogcatSettings.MAX_BUFFER_KB} (1KB-${LogcatSettings.MAX_BUFFER_KB / 1024}MB)",
                IdeColors.TextError,
            )
            buffer > LogcatSettings.LARGE_BUFFER_KB -> Note("Warning: large buffer size can cause performance degradation", IdeColors.TextWarning)
        }
        LabeledRow("Default filter for new Logcat windows:") {
            IdeTextField(
                settings.defaultFilter,
                { onChange(settings.copy(defaultFilter = it)) },
                Modifier.width(360.dp),
                placeholder = "e.g. level:info -tag:Choreographer",
                textStyle = IdeText.Mono.copy(color = IdeColors.Text),
            )
        }
        IdeCheckbox(
            settings.mostRecentlyUsedFilterIsDefault,
            { onChange(settings.copy(mostRecentlyUsedFilterIsDefault = it)) },
            "Use most-recently-used filter as the default",
        )
        IdeCheckbox(
            settings.filterHistoryAutocomplete,
            { onChange(settings.copy(filterHistoryAutocomplete = it)) },
            "Add filters from history to autocomplete",
        )
        Spacer(Modifier.height(4.dp))
        LabeledRow("Ignore tags:") {
            IdeTextField(
                ignoredTagsText,
                { ignoredTagsText = it; onChange(settings.copy(ignoredTags = splitValues(it))) },
                Modifier.width(420.dp),
                placeholder = "Choreographer chatty",
                textStyle = IdeText.Mono.copy(color = IdeColors.Text),
            )
        }
        LabeledRow("Ignore apps:") {
            IdeTextField(
                ignoredAppsText,
                { ignoredAppsText = it; onChange(settings.copy(ignoredApps = splitValues(it))) },
                Modifier.width(420.dp),
                placeholder = "com.google.android.gms",
                textStyle = IdeText.Mono.copy(color = IdeColors.Text),
            )
        }
        Note("Separate values with spaces or commas. Messages from ignored tags and apps are never shown.", IdeColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IdeCheckbox(settings.overrideFontSize, { onChange(settings.copy(overrideFontSize = it)) }, "Override font size")
            Spacer(Modifier.width(12.dp))
            IdeTextField(
                fontText,
                onFontText,
                Modifier.width(70.dp),
                enabled = settings.overrideFontSize,
                error = settings.overrideFontSize && fontText.trim().toIntOrNull() !in FONT_RANGE,
            )
        }
    }
}

private fun splitValues(text: String): Set<String> =
    text.split(',', ' ', '\t', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())

// ---- Logcat Format (Modify Views) --------------------------------------------------------

@Composable
private fun FormatPage(format: FormatSettings, colors: LogColors, onChange: (FormatSettings) -> Unit) {
    var style by remember { mutableStateOf(format.defaultStyle) }
    val options = format.options(style)
    fun set(o: FormattingOptions) = onChange(format.with(style, o))

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledRow("View:") {
            IdeDropdown(style, FormatStyle.entries, { it.displayName }, { style = it }, Modifier.width(160.dp))
        }
        Section("Timestamp") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IdeCheckbox(options.timestamp.enabled, { set(options.copy(timestamp = options.timestamp.copy(enabled = it))) }, "Show timestamp")
                Text("Format:", style = IdeText.Default.copy(color = if (options.timestamp.enabled) IdeColors.Text else IdeColors.TextDisabled))
                IdeRadioButton(options.timestamp.style == TimestampStyle.DATETIME, { set(options.copy(timestamp = options.timestamp.copy(style = TimestampStyle.DATETIME))) }, "Datetime", enabled = options.timestamp.enabled)
                IdeRadioButton(options.timestamp.style == TimestampStyle.TIME, { set(options.copy(timestamp = options.timestamp.copy(style = TimestampStyle.TIME))) }, "Time", enabled = options.timestamp.enabled)
            }
        }
        Section("Process ID") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IdeCheckbox(options.processThread.enabled, { set(options.copy(processThread = options.processThread.copy(enabled = it))) }, "Show process id")
                IdeCheckbox(options.processThread.includeThreadId, { set(options.copy(processThread = options.processThread.copy(includeThreadId = it))) }, "Include thread id", enabled = options.processThread.enabled)
            }
        }
        Section("Tag") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IdeCheckbox(options.tag.enabled, { set(options.copy(tag = options.tag.copy(enabled = it))) }, "Show tag")
                IdeCheckbox(!options.tag.hideDuplicates, { set(options.copy(tag = options.tag.copy(hideDuplicates = !it))) }, "Show repeated tags", enabled = options.tag.enabled)
                IdeCheckbox(options.tag.colorize, { set(options.copy(tag = options.tag.copy(colorize = it))) }, "Colorize", enabled = options.tag.enabled)
                WidthField("Tag column width:", options.tag.maxLength, options.tag.enabled) { set(options.copy(tag = options.tag.copy(maxLength = it))) }
            }
        }
        Section("Package Name") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IdeCheckbox(options.appName.enabled, { set(options.copy(appName = options.appName.copy(enabled = it))) }, "Show package name")
                IdeCheckbox(!options.appName.hideDuplicates, { set(options.copy(appName = options.appName.copy(hideDuplicates = !it))) }, "Show repeated package names", enabled = options.appName.enabled)
                WidthField("Package column width:", options.appName.maxLength, options.appName.enabled) { set(options.copy(appName = options.appName.copy(maxLength = it))) }
            }
        }
        Section("Process Name") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IdeCheckbox(options.processName.enabled, { set(options.copy(processName = options.processName.copy(enabled = it))) }, "Show process name")
                IdeCheckbox(!options.processName.hideDuplicates, { set(options.copy(processName = options.processName.copy(hideDuplicates = !it))) }, "Show repeated process names", enabled = options.processName.enabled)
                WidthField("Process column width:", options.processName.maxLength, options.processName.enabled) { set(options.copy(processName = options.processName.copy(maxLength = it))) }
            }
        }
        Section("Level") {
            IdeCheckbox(options.level, { set(options.copy(level = it)) }, "Show level")
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IdeCheckbox(format.defaultStyle == style, { if (it) onChange(format.copy(defaultStyle = style)) }, "Use as default view for new windows")
            Spacer(Modifier.weight(1f))
            IdeButton("Restore default", {
                set(if (style == FormatStyle.STANDARD) FormattingOptions.STANDARD else FormattingOptions.COMPACT)
            })
        }
        Spacer(Modifier.height(6.dp))
        Text("Preview", style = IdeText.Secondary)
        LogPreview(options, colors)
    }
}

@Composable
private fun WidthField(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.trim().toIntOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = IdeText.Default.copy(color = if (enabled) IdeColors.Text else IdeColors.TextDisabled))
        Spacer(Modifier.width(6.dp))
        IdeTextField(
            text,
            { t ->
                text = t
                t.trim().toIntOrNull()?.takeIf { it in 1..200 }?.let(onChange)
            },
            Modifier.width(56.dp),
            enabled = enabled,
            error = enabled && (parsed == null || parsed !in 1..200),
        )
    }
}

// ---- Color Scheme (Android Logcat) -------------------------------------------------------

@Composable
private fun ColorSchemePage(scheme: LogcatColorScheme, previewOptions: FormattingOptions, onChange: (LogcatColorScheme) -> Unit) {
    fun set(level: LogLevel, colors: LevelColors) = onChange(scheme.copy(levels = scheme.levels + (level to colors)))

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Note("Colors for log levels, as in Android Studio's Editor → Color Scheme → Android Logcat.", IdeColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.padding(bottom = 2.dp)) {
            Text("", Modifier.width(110.dp))
            Text("Indicator text", Modifier.width(170.dp), style = IdeText.Secondary)
            Text("Indicator background", Modifier.width(170.dp), style = IdeText.Secondary)
            Text("Message", style = IdeText.Secondary)
        }
        LogLevel.entries.forEach { level ->
            val c = scheme.forLevel(level)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(level.displayName, Modifier.width(110.dp), style = IdeText.Default)
                ColorField(c.indicatorForeground, Modifier.width(170.dp)) { set(level, c.copy(indicatorForeground = it)) }
                ColorField(c.indicatorBackground, Modifier.width(170.dp)) { set(level, c.copy(indicatorBackground = it)) }
                ColorField(c.message) { set(level, c.copy(message = it)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Spacer(Modifier.weight(1f))
            IdeButton("Restore defaults", { onChange(LogcatColorScheme()) })
        }
        Spacer(Modifier.height(6.dp))
        Text("Preview", style = IdeText.Secondary)
        LogPreview(previewOptions, LogColors(scheme))
    }
}

@Composable
private fun ColorField(hex: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var text by remember(hex) { mutableStateOf(hex) }
    val parsed = LogColors.parseHex(text)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(20.dp)
                .background(parsed ?: Color.Transparent, RoundedCornerShape(3.dp))
                .border(1.dp, IdeColors.ControlBorderRaised, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(6.dp))
        IdeTextField(
            text,
            { t ->
                val clean = t.trim().removePrefix("#").take(8)
                text = clean
                if (LogColors.parseHex(clean) != null && clean.length == 6) onChange(clean.uppercase())
            },
            Modifier.width(96.dp),
            error = parsed == null,
            textStyle = IdeText.Mono.copy(color = IdeColors.Text, fontFamily = FontFamily.Monospace),
        )
    }
}

// ---- Shared pieces --------------------------------------------------------------------

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = IdeText.Default)
        Spacer(Modifier.width(10.dp))
        content()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = IdeText.Bold)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(1.dp).background(IdeColors.Separator))
        }
        Column(Modifier.padding(start = 16.dp, top = 4.dp), content = content)
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, style = IdeText.Small.copy(color = color))
}
