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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.logcatgui.AppServices
import dev.logcatgui.LocalAppServices
import dev.logcatgui.LocalAwtWindow
import dev.logcatgui.Notification
import dev.logcatgui.adb.Device
import dev.logcatgui.adb.ScreenRecorder
import dev.logcatgui.platform.FileDialogs
import dev.logcatgui.platform.Shell
import dev.logcatgui.ui.components.HorizontalLine
import dev.logcatgui.ui.components.IdeButton
import dev.logcatgui.ui.components.IdeCheckbox
import dev.logcatgui.ui.components.IdeTextField
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class Phase { OPTIONS, STARTING, RECORDING, FINISHING }

/**
 * Android Studio's "Record Screen": options, then record with a timer, then save the MP4.
 * Device work runs on the app scope so closing the dialog can't interrupt a copy halfway.
 */
@Composable
fun ScreenRecorderDialog(device: Device, onClose: () -> Unit) {
    val services = LocalAppServices.current
    val recorder = remember { ScreenRecorder(services.adb, device.serial) }
    var phase by remember { mutableStateOf(Phase.OPTIONS) }
    var bitRateText by remember { mutableStateOf("4") }
    var showTaps by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val parentHolder = remember { arrayOfNulls<java.awt.Window>(1) }
    val bitRate = bitRateText.trim().toIntOrNull()?.takeIf { it in 1..100 }

    fun finish() {
        if (phase != Phase.RECORDING) return
        phase = Phase.FINISHING
        services.scope.launch {
            recorder.stop()
            saveRecording(services, recorder, device, parentHolder[0])
            onClose()
        }
    }

    // Closing mid-recording stops and discards it.
    DisposableEffect(Unit) {
        onDispose {
            if (phase == Phase.RECORDING || phase == Phase.STARTING) {
                services.scope.launch {
                    recorder.stop()
                    recorder.discard()
                }
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase != Phase.RECORDING) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (recorder.isRecording) {
            elapsedSeconds = ((System.currentTimeMillis() - start) / 1000).toInt()
            delay(250)
        }
        finish() // screenrecord stopped by itself (time limit)
    }

    IdeDialog("Screen Recorder — ${device.name}", onCloseRequest = onClose, size = DpSize(520.dp, 330.dp), resizable = false) {
        val awtWindow = LocalAwtWindow.current
        SideEffect { parentHolder[0] = awtWindow }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (phase) {
                    Phase.OPTIONS, Phase.STARTING -> {
                        Text("Record the screen of ${device.label}.", style = IdeText.Default)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bit rate:", style = IdeText.Default)
                            Spacer(Modifier.width(10.dp))
                            IdeTextField(bitRateText, { bitRateText = it }, Modifier.width(70.dp), error = bitRate == null)
                            Text("  Mbps", style = IdeText.Default)
                        }
                        IdeCheckbox(showTaps, { showTaps = it }, "Show taps")
                        Text(
                            "Android stops a recording after 3 minutes. The video is saved on the device first, then copied to your computer.",
                            style = IdeText.Small.copy(color = IdeColors.TextSecondary),
                        )
                    }
                    Phase.RECORDING -> {
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).background(Color(0xFFDB5C5C), CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Recording  ${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}",
                                style = IdeText.Bold.copy(fontSize = 20.sp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Phase.FINISHING -> {
                        Spacer(Modifier.weight(1f))
                        Text("Finishing the recording and copying it from the device…", Modifier.align(Alignment.CenterHorizontally), style = IdeText.Secondary)
                        Spacer(Modifier.weight(1f))
                    }
                }
                error?.let { Text(it, style = IdeText.Default.copy(color = IdeColors.TextError)) }
            }
            HorizontalLine(color = IdeColors.PanelBorder)
            DialogButtons {
                when (phase) {
                    Phase.OPTIONS, Phase.STARTING -> {
                        IdeButton("Cancel", onClose)
                        IdeButton(
                            "Start Recording",
                            {
                                phase = Phase.STARTING
                                error = null
                                services.scope.launch {
                                    try {
                                        recorder.start(bitRate ?: 4, showTaps)
                                        phase = Phase.RECORDING
                                    } catch (e: Exception) {
                                        error = "Could not start recording: ${e.message}"
                                        phase = Phase.OPTIONS
                                    }
                                }
                            },
                            default = true,
                            enabled = bitRate != null && phase == Phase.OPTIONS,
                        )
                    }
                    Phase.RECORDING -> IdeButton("Stop Recording", ::finish, default = true)
                    Phase.FINISHING -> IdeButton("Close", onClose, enabled = false)
                }
            }
        }
    }
}

private suspend fun saveRecording(services: AppServices, recorder: ScreenRecorder, device: Device, parent: java.awt.Window?) {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val name = "ScreenRecording_${device.name.replace(Regex("[^A-Za-z0-9]+"), "_")}_$stamp.mp4"
    val chosen = FileDialogs.save(parent, "Save Screen Recording", services.settings.settings.lastDirectory, name)
    if (chosen == null) {
        recorder.discard()
        return
    }
    val file = if (chosen.extension.isEmpty()) File(chosen.path + ".mp4") else chosen
    try {
        recorder.pullTo(file)
        services.settings.update { it.copy(lastDirectory = file.parent) }
        services.notifications.post(
            "Screen recording saved: ${file.name}",
            Notification.Kind.INFO,
            "Open" to { Shell.open(file) },
            "Show in folder" to { Shell.reveal(file) },
        )
    } catch (e: Exception) {
        services.notifications.post("Could not save the screen recording: ${e.message}", Notification.Kind.ERROR)
    }
}
