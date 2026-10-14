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

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/** The IDE's fonts: Inter for UI text, JetBrains Mono for the log. Both ship with Android Studio. */
object IdeFonts {
    val Inter: FontFamily = FontFamily(
        font("Inter-Regular.otf", FontWeight.Normal),
        font("Inter-SemiBold.otf", FontWeight.SemiBold),
    )
    val JetBrainsMono: FontFamily = FontFamily(
        font("JetBrainsMono-Regular.ttf", FontWeight.Normal),
        font("JetBrainsMono-Bold.ttf", FontWeight.Bold),
    )

    private fun font(file: String, weight: FontWeight) = Font(
        identity = file,
        data = IdeFonts::class.java.getResourceAsStream("/fonts/$file")!!.use { it.readBytes() },
        weight = weight,
        style = FontStyle.Normal,
    )
}

/** IntelliJ's default UI text: Inter 13. */
object IdeText {
    val Default = TextStyle(fontFamily = IdeFonts.Inter, fontSize = 13.sp, color = IdeColors.Text)
    val Small = Default.copy(fontSize = 12.sp)
    val Bold = Default.copy(fontWeight = FontWeight.SemiBold)
    val Secondary = Default.copy(color = IdeColors.TextSecondary)
    val Mono = TextStyle(fontFamily = IdeFonts.JetBrainsMono, fontSize = 13.sp, color = IdeColors.EditorText)
}

private val MaterialColors = darkColorScheme(
    primary = IdeColors.FocusBorder,
    onPrimary = IdeColors.TextOverAccent,
    background = IdeColors.ToolWindowBg,
    onBackground = IdeColors.Text,
    surface = IdeColors.PopupBg,
    onSurface = IdeColors.Text,
    surfaceVariant = IdeColors.PopupBg,
    onSurfaceVariant = IdeColors.Text,
    surfaceContainer = IdeColors.PopupBg,
    outline = IdeColors.ControlBorder,
    error = IdeColors.TextError,
)

private val SelectionColors = TextSelectionColors(
    handleColor = IdeColors.FocusBorder,
    backgroundColor = IdeColors.EditorSelection,
)

@Composable
fun IdeTheme(logColors: LogColors = LogColors.Default, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialColors) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides SelectionColors,
            LocalTextStyle provides IdeText.Default,
            LocalContentColor provides IdeColors.Text,
            LocalLogColors provides logColors,
            content = content,
        )
    }
}
