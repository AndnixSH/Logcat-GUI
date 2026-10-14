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
package dev.logcatgui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.logcatgui.platform.WindowChrome
import dev.logcatgui.platform.isMac
import java.awt.Desktop
import dev.logcatgui.ui.dialogs.AboutDialog
import dev.logcatgui.ui.dialogs.ScreenRecorderDialog
import dev.logcatgui.ui.dialogs.ScreenshotDialog
import dev.logcatgui.ui.dialogs.SettingsDialog
import dev.logcatgui.ui.panel.LocalPanelHost
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeTheme
import dev.logcatgui.ui.theme.Icons
import dev.logcatgui.ui.theme.LogColors
import dev.logcatgui.ui.window.AppController
import dev.logcatgui.ui.window.LogcatWindow

fun main() {
    // The UI is dark-only; on macOS this makes the native title bar dark too. Must be set
    // before AWT starts.
    if (isMac) System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")
    application { App() }
}

@Composable
private fun ApplicationScope.App() {
    val services = remember { AppServices() }
    val controller = remember { AppController(services) }
    DisposableEffect(Unit) {
        onDispose {
            controller.dispose()
            services.dispose()
        }
    }
    // macOS app menu: "About Logcat GUI" and "Settings…" (⌘,) open our dialogs.
    LaunchedEffect(Unit) {
        if (!Desktop.isDesktopSupported()) return@LaunchedEffect
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) desktop.setAboutHandler { controller.aboutOpen = true }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler { controller.openSettings() }
    }
    val colorScheme = services.settings.settings.colors
    val logColors = remember(colorScheme) { LogColors(colorScheme) }

    Window(
        onCloseRequest = ::exitApplication,
        title = AppInfo.NAME,
        icon = remember { Icons.painter(Icons.AppIcon) },
        state = rememberWindowState(size = DpSize(1360.dp, 860.dp)),
        onPreviewKeyEvent = { e ->
            val settingsShortcut = (e.isCtrlPressed && e.isAltPressed && e.key == Key.S) ||
                (isMac && e.isMetaPressed && e.key == Key.Comma)
            if (e.type == KeyEventType.KeyDown && settingsShortcut) {
                controller.openSettings()
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            controller.awtWindow = window
            window.background = java.awt.Color(0x26, 0x28, 0x2C)
            WindowChrome.applyDark(window, caption = IdeColors.MainWindowBg, text = IdeColors.Text, border = IdeColors.MainWindowBg)
        }
        IdeTheme(logColors) {
            CompositionLocalProvider(
                LocalAppServices provides services,
                LocalAwtWindow provides window,
                LocalPanelHost provides controller,
            ) {
                LogcatWindow(controller)
                controller.settingsPage?.let { page -> SettingsDialog(page, onClose = { controller.settingsPage = null }) }
                if (controller.aboutOpen) AboutDialog(onClose = { controller.aboutOpen = false })
                controller.screenshotDevice?.let { device -> ScreenshotDialog(device, onClose = { controller.screenshotDevice = null }) }
                controller.recorderDevice?.let { device -> ScreenRecorderDialog(device, onClose = { controller.recorderDevice = null }) }
            }
        }
    }
}
