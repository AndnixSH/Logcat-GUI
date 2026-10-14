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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import dev.logcatgui.LocalAwtWindow
import dev.logcatgui.platform.WindowChrome
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.Icons

/** A dialog window with the IDE's dark chrome; Escape closes it. */
@Composable
fun IdeDialog(
    title: String,
    onCloseRequest: () -> Unit,
    size: DpSize,
    resizable: Boolean = true,
    content: @Composable () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = rememberDialogState(size = size),
        title = title,
        icon = remember { Icons.painter(Icons.AppIcon) },
        resizable = resizable,
        onPreviewKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                onCloseRequest()
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            window.background = java.awt.Color(0x19, 0x1A, 0x1C)
            WindowChrome.applyDark(window, caption = IdeColors.DialogBg, text = IdeColors.Text, border = IdeColors.PopupBorder)
        }
        CompositionLocalProvider(LocalAwtWindow provides window) {
            Box(Modifier.fillMaxSize().background(IdeColors.DialogBg)) { content() }
        }
    }
}

/** Right-aligned dialog buttons (Cancel / Apply / OK …). */
@Composable
fun DialogButtons(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
