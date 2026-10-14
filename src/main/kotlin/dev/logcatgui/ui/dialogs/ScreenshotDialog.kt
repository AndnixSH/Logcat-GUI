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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.logcatgui.LocalAppServices
import dev.logcatgui.LocalAwtWindow
import dev.logcatgui.Notification
import dev.logcatgui.adb.Device
import dev.logcatgui.adb.ScreenCapture
import dev.logcatgui.platform.Clipboard
import dev.logcatgui.platform.FileDialogs
import dev.logcatgui.platform.Shell
import dev.logcatgui.ui.components.HorizontalLine
import dev.logcatgui.ui.components.IdeButton
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Android Studio's "Take Screenshot": capture, preview, copy or save as PNG. */
@Composable
fun ScreenshotDialog(device: Device, onClose: () -> Unit) {
    val services = LocalAppServices.current
    var png by remember { mutableStateOf<ByteArray?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        capturing = true
        error = null
        try {
            val bytes = ScreenCapture.capture(services.adb, device.serial)
            image = withContext(Dispatchers.Default) { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
            png = bytes
        } catch (e: Exception) {
            error = "Could not take a screenshot: ${e.message}"
        } finally {
            capturing = false
        }
    }

    IdeDialog("Screenshot — ${device.name}", onCloseRequest = onClose, size = DpSize(560.dp, 880.dp)) {
        val awtWindow = LocalAwtWindow.current
        fun save() {
            val bytes = png ?: return
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val name = "Screenshot_${device.name.replace(Regex("[^A-Za-z0-9]+"), "_")}_$stamp.png"
            val chosen = FileDialogs.save(awtWindow, "Save Screenshot", services.settings.settings.lastDirectory, name) ?: return
            val file = if (chosen.extension.isEmpty()) File(chosen.path + ".png") else chosen
            try {
                file.writeBytes(bytes)
                services.settings.update { it.copy(lastDirectory = file.parent) }
                services.notifications.post(
                    "Screenshot saved: ${file.name}",
                    Notification.Kind.INFO,
                    "Open" to { Shell.open(file) },
                    "Show in folder" to { Shell.reveal(file) },
                )
                onClose()
            } catch (e: IOException) {
                error = "Could not save: ${e.message}"
            }
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxWidth().background(IdeColors.EditorBg).padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = image
                when {
                    error != null -> Text(error!!, style = IdeText.Default.copy(color = IdeColors.TextError))
                    bitmap != null -> Image(bitmap, contentDescription = "Screenshot", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else -> Text("Capturing…", style = IdeText.Secondary)
                }
            }
            HorizontalLine(color = IdeColors.PanelBorder)
            DialogButtons {
                IdeButton("Recapture", { attempt++ }, enabled = !capturing)
                IdeButton("Copy to Clipboard", {
                    png?.let {
                        Clipboard.copyPng(it)
                        services.notifications.post("Screenshot copied to the clipboard")
                    }
                }, enabled = png != null && !capturing)
                Spacer(Modifier.weight(1f))
                IdeButton("Close", onClose)
                IdeButton("Save…", ::save, default = true, enabled = png != null && !capturing)
            }
        }
    }
}
