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

import androidx.compose.runtime.staticCompositionLocalOf
import dev.logcatgui.adb.Device
import java.io.File

enum class SplitOrientation { SIDE_BY_SIDE, STACKED }

/** Window-level actions a panel's toolbar triggers (tabs, splits, dialogs, file choosers). */
interface PanelHost {
    fun importFile()
    fun openFile(file: File)
    fun exportPanel(panel: LogcatPanelState)
    fun chooseMapping(panel: LogcatPanelState)
    fun split(panel: LogcatPanelState, orientation: SplitOrientation)
    fun close(panel: LogcatPanelState)
    fun canClose(panel: LogcatPanelState): Boolean
    fun openScreenshot(device: Device)
    fun openScreenRecorder(device: Device)
    fun openFormatSettings()
}

val LocalPanelHost = staticCompositionLocalOf<PanelHost> { error("No PanelHost") }
