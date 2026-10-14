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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.logcatgui.adb.Device
import dev.logcatgui.ui.components.IdePopup
import dev.logcatgui.ui.components.PopupRow
import dev.logcatgui.ui.components.listKeyHandler
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.Icon
import dev.logcatgui.ui.theme.Icons

private fun deviceIcon(device: Device) = if (device.isEmulator) Icons.DeviceVirtual else Icons.DevicePhysical

/**
 * Android Studio's device picker: the selected device (or imported file) with a popup of
 * every device seen this session, offline ones marked `[OFFLINE]`.
 */
@Composable
fun DeviceComboBox(panel: LogcatPanelState, devices: List<Device>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(4.dp)
    val source = panel.source
    val current = panel.device(devices)

    Box(modifier.onSizeChanged { anchorWidth = it.width }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(IdeColors.ControlBgRaised, shape)
                .border(if (open) 2.dp else 1.dp, if (open) IdeColors.FocusBorder else IdeColors.ControlBorder, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true }
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                source is PanelSource.FileSource -> {
                    Icon(Icons.TextFile)
                    Spacer(Modifier.width(6.dp))
                    Text(source.file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                current != null -> {
                    Icon(deviceIcon(current))
                    Spacer(Modifier.width(6.dp))
                    val label = buildAnnotatedString {
                        append(current.label)
                        current.statusTag?.let { tag ->
                            withStyle(SpanStyle(color = IdeColors.TextSecondary)) { append("  $tag") }
                        }
                    }
                    Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                else -> Text(
                    if (devices.isEmpty()) "No connected devices" else "Select a device",
                    Modifier.weight(1f),
                    style = IdeText.Secondary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.ChevronDown)
        }
        if (open) {
            DevicePopup(
                panel = panel,
                devices = devices,
                minWidthPx = anchorWidth,
                onDismiss = { open = false },
            )
        }
    }
}

@Composable
private fun DevicePopup(panel: LogcatPanelState, devices: List<Device>, minWidthPx: Int, onDismiss: () -> Unit) {
    val minWidth = with(LocalDensity.current) { minWidthPx.toDp() }
    val fileSource = panel.source as? PanelSource.FileSource
    // Row indices: the imported file (if any) first, then devices.
    val offset = if (fileSource != null) 1 else 0
    val rowCount = devices.size + offset
    val selectedRow = when {
        fileSource != null -> 0
        else -> devices.indexOfFirst { it.serial == panel.serial }.let { if (it >= 0) it + offset else -1 }
    }
    var highlighted by remember { mutableIntStateOf(selectedRow.coerceAtLeast(0)) }
    val focus = remember { FocusRequester() }

    fun choose(row: Int) {
        if (row < offset) {
            onDismiss()
            return
        }
        devices.getOrNull(row - offset)?.let { device ->
            onDismiss()
            panel.selectDevice(device)
        }
    }

    IdePopup(onDismissRequest = onDismiss, minWidth = minWidth) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    listKeyHandler(e, (0 until rowCount).toList(), highlighted, { highlighted = it }, ::choose, onDismiss)
                },
        ) {
            if (fileSource != null) {
                PopupRow(highlighted = highlighted == 0, onHover = { highlighted = 0 }, onClick = { choose(0) }) {
                    Icon(Icons.TextFile)
                    Spacer(Modifier.width(8.dp))
                    Text(fileSource.file.name, maxLines = 1)
                    fileSource.description?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = IdeText.Secondary, maxLines = 1)
                    }
                }
            }
            if (devices.isEmpty()) {
                Text(
                    "No devices. Connect a device or start an emulator.",
                    Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = IdeText.Secondary,
                )
            }
            devices.forEachIndexed { i, device ->
                val row = i + offset
                PopupRow(highlighted = highlighted == row, onHover = { highlighted = row }, onClick = { choose(row) }) {
                    Icon(deviceIcon(device))
                    Spacer(Modifier.width(8.dp))
                    Text(device.label, maxLines = 1)
                    val secondary = listOfNotNull(device.details.ifEmpty { null }, device.statusTag).joinToString(" ")
                    if (secondary.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(secondary, style = IdeText.Secondary, maxLines = 1)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
