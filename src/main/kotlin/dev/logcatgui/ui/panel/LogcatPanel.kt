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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.logcatgui.LocalAppServices
import dev.logcatgui.adb.Device
import dev.logcatgui.settings.FormatStyle
import dev.logcatgui.ui.components.ActionButton
import dev.logcatgui.ui.components.HorizontalLine
import dev.logcatgui.ui.components.MenuEntry
import dev.logcatgui.ui.components.MenuItem
import dev.logcatgui.ui.components.MenuSeparator
import dev.logcatgui.ui.components.OverflowToolbar
import dev.logcatgui.ui.components.ToolbarAction
import dev.logcatgui.ui.components.ToolbarItem
import dev.logcatgui.ui.components.ToolbarSeparator
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.Icons

/** One Logcat panel: device + filter row, action toolbar, log, and status line. */
@Composable
fun LogcatPanel(panel: LogcatPanelState, modifier: Modifier = Modifier) {
    val services = LocalAppServices.current
    val devices by services.devices.devices.collectAsState()

    Column(modifier.background(IdeColors.ToolWindowBg)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val comboWidth = (maxWidth * 0.3f).coerceIn(170.dp, 380.dp)
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceComboBox(panel, devices, Modifier.width(comboWidth))
                FilterField(panel, Modifier.weight(1f))
                var helpOpen by remember { mutableStateOf(false) }
                Box {
                    ActionButton(Icons.Help, "Logcat filter syntax", onClick = { helpOpen = true })
                    if (helpOpen) FilterHelpPopup(onDismiss = { helpOpen = false })
                }
            }
        }
        OverflowToolbar(
            items = toolbarItems(panel, panel.device(devices), LocalPanelHost.current),
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 6.dp),
        )
        HorizontalLine(color = IdeColors.PanelBorder)
        LogcatView(panel, Modifier.weight(1f).fillMaxWidth())
        StatusLine(panel)
    }
}

/**
 * Android Studio's Logcat toolbar laid out horizontally, in AS's order, without the
 * "Up/Down the Stack Trace" navigation actions.
 */
internal fun toolbarItems(panel: LogcatPanelState, device: Device?, host: PanelHost): List<ToolbarItem> {
    val online = device?.isOnline == true
    val isFile = panel.isFile
    return listOf(
        ToolbarAction(Icons.Clear, "Clear Logcat", panel::clear),
        ToolbarAction(
            if (panel.paused) Icons.Resume else Icons.Pause,
            if (panel.paused) "Resume Logcat" else "Pause Logcat",
            panel::togglePause,
            selected = panel.paused,
            enabled = !isFile,
        ),
        ToolbarAction(Icons.Restart, if (isFile) "Reload Logcat File" else "Restart Logcat", panel::restart, enabled = isFile || device != null),
        ToolbarAction(Icons.ScrollToEnd, "Scroll to the End", { panel.followTail = true }, selected = panel.followTail),
        ToolbarAction(Icons.SoftWrap, "Soft-Wrap", { panel.softWrap = !panel.softWrap }, selected = panel.softWrap),
        ToolbarSeparator,
        ToolbarAction(Icons.Import, "Import Logs from a File", host::importFile),
        ToolbarAction(Icons.Export, "Export Logs to a File", { host.exportPanel(panel) }, enabled = panel.visible.isNotEmpty()),
        ToolbarSeparator,
        ToolbarAction(Icons.Format, "Configure Logcat Formatting Options", menu = {
            FormatStyle.entries.map { style ->
                MenuItem("${style.displayName} View", { panel.formatStyle = style }, checked = panel.formatStyle == style)
            } + listOf<MenuEntry>(MenuSeparator, MenuItem("Modify Views…", host::openFormatSettings))
        }),
        ToolbarSeparator,
        ToolbarAction(Icons.Split, "Split Panels", menu = {
            listOf(
                MenuItem("Split Right", { host.split(panel, SplitOrientation.SIDE_BY_SIDE) }, icon = Icons.SplitRight),
                MenuItem("Split Down", { host.split(panel, SplitOrientation.STACKED) }, icon = Icons.SplitDown),
                MenuSeparator,
                MenuItem("Close Panel", { host.close(panel) }, enabled = host.canClose(panel)),
            )
        }),
        proguardAction(panel, host),
        ToolbarSeparator,
        ToolbarAction(Icons.Screenshot, "Take Screenshot", { device?.let(host::openScreenshot) }, enabled = online),
        ToolbarAction(Icons.ScreenRecord, "Record Screen", { device?.let(host::openScreenRecorder) }, enabled = online),
    )
}

private fun proguardAction(panel: LogcatPanelState, host: PanelHost): ToolbarAction {
    val retracer = panel.retracer
    return if (retracer == null) {
        ToolbarAction(Icons.Proguard, "Load Proguard Mappings…", { host.chooseMapping(panel) })
    } else {
        ToolbarAction(Icons.Proguard, "Proguard Mapping: ${retracer.fileName}", selected = true, menu = {
            listOf(
                MenuItem("Load Proguard Mappings…", { host.chooseMapping(panel) }),
                MenuItem("Remove Mapping (${retracer.fileName})", panel::clearMapping),
            )
        })
    }
}

@Composable
private fun StatusLine(panel: LogcatPanelState) {
    Row(
        Modifier.fillMaxWidth().height(24.dp).background(IdeColors.ToolWindowBg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val error = panel.filterError
        Text(
            text = if (error != null) "Filter: $error" else panel.status,
            style = IdeText.Small.copy(color = if (error != null) IdeColors.TextError else IdeColors.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (panel.paused) {
            Text("Paused · ${panel.pendingCount} new", style = IdeText.Small.copy(color = IdeColors.TextWarning), maxLines = 1)
        }
        panel.retracer?.let {
            Text("Mapping: ${it.fileName}", style = IdeText.Small.copy(color = IdeColors.TextSecondary), maxLines = 1)
        }
        Text("${panel.visible.size} of ${panel.totalCount}", style = IdeText.Small.copy(color = IdeColors.TextSecondary), maxLines = 1)
    }
}

/** Fills a split cell with a panel. */
@Composable
fun LogcatPanelCell(panel: LogcatPanelState) {
    LogcatPanel(panel, Modifier.fillMaxSize())
}
