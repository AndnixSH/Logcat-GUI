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
package dev.logcatgui.ui.window

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.logcatgui.LocalAppServices
import dev.logcatgui.Notification
import dev.logcatgui.ui.components.ActionButton
import dev.logcatgui.ui.components.IdeLink
import dev.logcatgui.ui.components.IdeMenu
import dev.logcatgui.ui.components.MenuItem
import dev.logcatgui.ui.components.MenuSeparator
import dev.logcatgui.ui.panel.LogcatPanel
import dev.logcatgui.ui.panel.SplitOrientation
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.Icon
import dev.logcatgui.ui.theme.Icons
import java.awt.Cursor

/**
 * The main window content: the Logcat tool window drawn as an Islands-theme "island"
 * (rounded, inset by `Island.borderWidth` over `MainWindow.background`), with the tool
 * window header (title, tabs, new tab, options) above the selected tab's panels.
 */
@Composable
fun LogcatWindow(controller: AppController) {
    Box(Modifier.fillMaxSize().background(IdeColors.MainWindowBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IdeColors.ToolWindowBg),
        ) {
            ToolWindowHeader(controller)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                controller.tabs.selected?.let { tab -> key(tab.id) { PanelTree(tab.root) } }
            }
        }
        NotificationsOverlay(Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
}

@Composable
private fun ToolWindowHeader(controller: AppController) {
    val tabs = controller.tabs
    var optionsOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Logcat", style = IdeText.Bold)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.tabs.forEachIndexed { index, tab ->
                    key(tab.id) {
                        TabItem(
                            tab = tab,
                            selected = index == tabs.selectedIndex,
                            onSelect = { tabs.select(tab) },
                            onClose = { tabs.close(tab) },
                            onCloseOthers = { tabs.closeOthers(tab) },
                            canCloseOthers = tabs.tabs.size > 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(2.dp))
            ActionButton(Icons.Add, "New Tab", onClick = { tabs.newTab() })
        }
        Box {
            ActionButton(Icons.MoreVertical, "Options", onClick = { optionsOpen = true })
            if (optionsOpen) {
                IdeMenu(
                    entries = listOf(
                        MenuItem("Settings…", { controller.openSettings() }, icon = Icons.Settings, trailing = "Ctrl+Alt+S"),
                        MenuItem("Logcat Format…", { controller.openSettings(SettingsPage.FORMAT) }, icon = Icons.Format),
                        MenuItem("Import Logs from a File…", controller::importFile, icon = Icons.Import),
                        MenuSeparator,
                        MenuItem("About Logcat GUI", { controller.aboutOpen = true }, icon = Icons.Info),
                    ),
                    onDismissRequest = { optionsOpen = false },
                    alignEnd = true,
                )
            }
        }
    }
}

/** A tool window tab: click to select, double-click to rename, middle-click to close, right-click for a menu. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun TabItem(
    tab: LogcatTab,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
    canCloseOthers: Boolean,
) {
    var renaming by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .height(28.dp)
                .background(
                    when {
                        selected -> IdeColors.TabSelectedBg
                        hovered -> IdeColors.TabHoverBg
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(6.dp),
                )
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { e ->
                    when {
                        e.buttons.isSecondaryPressed -> menuOpen = true
                        e.buttons.isTertiaryPressed -> onClose()
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onSelect,
                    onDoubleClick = { renaming = true },
                )
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (renaming) {
                RenameField(tab.title) { name ->
                    if (name.isNotBlank()) tab.title = name.trim()
                    renaming = false
                }
            } else {
                Text(tab.title, style = IdeText.Default, maxLines = 1)
            }
            Spacer(Modifier.width(4.dp))
            CloseTabButton(onClose)
        }
        if (menuOpen) {
            IdeMenu(
                entries = listOf(
                    MenuItem("Rename Tab", { renaming = true }),
                    MenuSeparator,
                    MenuItem("Close Tab", onClose),
                    MenuItem("Close Other Tabs", onCloseOthers, enabled = canCloseOthers),
                ),
                onDismissRequest = { menuOpen = false },
            )
        }
    }
}

@Composable
private fun CloseTabButton(onClose: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(18.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (hovered) Icons.CloseSmallHovered else Icons.CloseSmall)
    }
}

@Composable
private fun RenameField(initial: String, onDone: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(3.dp)
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        textStyle = IdeText.Default,
        cursorBrush = SolidColor(IdeColors.Text),
        modifier = Modifier
            .width(140.dp)
            .background(IdeColors.ControlBg, shape)
            .border(1.dp, IdeColors.FocusBorder, shape)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) onDone(value.text) }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> { onDone(value.text); true }
                    Key.Escape -> { onDone(initial); true }
                    else -> false
                }
            },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Renders a tab's layout tree; split dividers can be dragged. */
@Composable
fun PanelTree(node: PanelNode) {
    when (node) {
        is LeafNode -> key(node.panel) { LogcatPanel(node.panel, Modifier.fillMaxSize()) }
        is SplitNode -> SplitPane(node)
    }
}

@Composable
private fun SplitPane(node: SplitNode) {
    val sideBySide = node.orientation == SplitOrientation.SIDE_BY_SIDE
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total by rememberUpdatedState(
            (if (sideBySide) constraints.maxWidth else constraints.maxHeight).toFloat().coerceAtLeast(1f)
        )
        val drag = rememberDraggableState { delta -> node.ratio = (node.ratio + delta / total).coerceIn(0.1f, 0.9f) }
        val divider = Modifier
            .draggable(drag, if (sideBySide) Orientation.Horizontal else Orientation.Vertical)
            .pointerHoverIcon(PointerIcon(Cursor(if (sideBySide) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR)))
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(node.ratio).fillMaxHeight()) { PanelTree(node.first) }
                Box(divider.width(5.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(IdeColors.PanelBorder))
                }
                Box(Modifier.weight(1f - node.ratio).fillMaxHeight()) { PanelTree(node.second) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(node.ratio).fillMaxWidth()) { PanelTree(node.first) }
                Box(divider.height(5.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.height(1.dp).fillMaxWidth().background(IdeColors.PanelBorder))
                }
                Box(Modifier.weight(1f - node.ratio).fillMaxWidth()) { PanelTree(node.second) }
            }
        }
    }
}

/** IntelliJ-style notification balloons, newest at the bottom. */
@Composable
private fun NotificationsOverlay(modifier: Modifier = Modifier) {
    val center = LocalAppServices.current.notifications
    Column(modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        center.active.forEach { n ->
            key(n) {
                val shape = RoundedCornerShape(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(IdeColors.TooltipBg, shape)
                        .border(1.dp, IdeColors.TooltipBorder, shape)
                        .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Box(
                        Modifier.padding(top = 2.dp).size(8.dp).background(
                            when (n.kind) {
                                Notification.Kind.INFO -> IdeColors.TextLink
                                Notification.Kind.WARNING -> IdeColors.TextWarning
                                Notification.Kind.ERROR -> IdeColors.TextError
                            },
                            RoundedCornerShape(4.dp),
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.text, style = IdeText.Default)
                        if (n.actions.isNotEmpty()) {
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                n.actions.forEach { (label, action) ->
                                    IdeLink(label, onClick = {
                                        center.dismiss(n)
                                        action()
                                    })
                                }
                            }
                        }
                    }
                    ActionButton(Icons.CloseSmall, null, onClick = { center.dismiss(n) }, size = 20.dp)
                }
            }
        }
    }
}
