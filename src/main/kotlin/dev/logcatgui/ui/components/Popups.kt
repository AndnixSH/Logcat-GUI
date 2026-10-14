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
package dev.logcatgui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.Icon
import dev.logcatgui.ui.theme.Icons
import kotlin.math.max

/** Places a popup below its anchor (or above, if it doesn't fit), clamped to the window. */
private class DropdownPositionProvider(
    private val gapPx: Int,
    private val alignEnd: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        var x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        x = x.coerceIn(0, max(0, windowSize.width - popupContentSize.width))
        var y = anchorBounds.bottom + gapPx
        if (y + popupContentSize.height > windowSize.height) {
            val above = anchorBounds.top - gapPx - popupContentSize.height
            y = if (above >= 0) above else max(0, windowSize.height - popupContentSize.height)
        }
        return IntOffset(x, y)
    }
}

/**
 * A popup anchored to the composable it's called from, styled like IntelliJ popups:
 * `Popup.background`, `Popup.borderColor`, 8dp corners, 4dp vertical list insets.
 */
@Composable
fun IdePopup(
    onDismissRequest: () -> Unit,
    focusable: Boolean = true,
    alignEnd: Boolean = false,
    minWidth: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val provider = remember(density, alignEnd) {
        DropdownPositionProvider(with(density) { 3.dp.roundToPx() }, alignEnd)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = focusable),
    ) {
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .widthIn(min = minWidth)
                .background(IdeColors.PopupBg, shape)
                .border(1.dp, IdeColors.PopupBorder, shape)
                .padding(vertical = 4.dp),
        ) {
            content()
        }
    }
}

sealed interface MenuEntry

data class MenuItem(
    val text: String,
    val onClick: () -> Unit,
    val icon: String? = null,
    /** Shows a check mark (radio/toggle state). */
    val checked: Boolean = false,
    val enabled: Boolean = true,
    /** Right-aligned muted text, e.g. a shortcut. */
    val trailing: String? = null,
) : MenuEntry

data object MenuSeparator : MenuEntry

data class MenuHeader(val text: String) : MenuEntry

/** A popup menu with hover tracking and Up/Down/Enter/Escape keyboard navigation. */
@Composable
fun IdeMenu(entries: List<MenuEntry>, onDismissRequest: () -> Unit, alignEnd: Boolean = false, minWidth: Dp = 180.dp) {
    IdePopup(onDismissRequest = onDismissRequest, alignEnd = alignEnd, minWidth = minWidth) {
        val selectable = entries.indices.filter { (entries[it] as? MenuItem)?.enabled == true }
        var highlighted by remember { mutableIntStateOf(-1) }
        val focus = remember { FocusRequester() }
        val hasChecks = entries.any { it is MenuItem && it.checked }
        val hasIcons = entries.any { it is MenuItem && it.icon != null }

        fun activate(index: Int) {
            val item = entries.getOrNull(index) as? MenuItem ?: return
            if (!item.enabled) return
            onDismissRequest()
            item.onClick()
        }

        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    listKeyHandler(e, selectable, highlighted, onMove = { highlighted = it }, onEnter = ::activate, onEscape = onDismissRequest)
                },
        ) {
            entries.forEachIndexed { index, entry ->
                when (entry) {
                    is MenuItem -> PopupRow(
                        highlighted = highlighted == index,
                        enabled = entry.enabled,
                        onHover = { if (entry.enabled) highlighted = index },
                        onClick = { activate(index) },
                    ) {
                        if (hasChecks) {
                            Box(Modifier.width(22.dp)) { if (entry.checked) Icon(Icons.Checked, size = 14.dp) }
                        }
                        if (hasIcons) {
                            Box(Modifier.width(24.dp)) { entry.icon?.let { Icon(it, alpha = if (entry.enabled) 1f else 0.38f) } }
                        }
                        Text(
                            entry.text,
                            style = IdeText.Default.copy(color = if (entry.enabled) IdeColors.Text else IdeColors.TextDisabled),
                            maxLines = 1,
                        )
                        if (entry.trailing != null) {
                            Spacer(Modifier.weight(1f).widthIn(min = 24.dp))
                            Text(entry.trailing, style = IdeText.Default.copy(color = IdeColors.TextSecondary), maxLines = 1)
                        }
                    }
                    MenuSeparator -> Box(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).height(1.dp).background(IdeColors.Separator),
                    )
                    is MenuHeader -> Text(
                        entry.text,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 2.dp),
                        style = IdeText.Small.copy(color = IdeColors.TextSecondary),
                    )
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/**
 * One row of a popup list: 24dp tall (`List.rowHeight`), inset selection with rounded
 * corners in `selection-bg-active`, as in the new UI.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PopupRow(
    highlighted: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(height)
            .background(if (highlighted) IdeColors.SelectionBg else Color.Transparent, RoundedCornerShape(4.dp))
            .onPointerEvent(PointerEventType.Enter) { onHover() }
            .onPointerEvent(PointerEventType.Move) { onHover() }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** Shared Up/Down/Home/End/Enter/Escape handling for keyboard-navigable lists. */
fun listKeyHandler(
    e: KeyEvent,
    selectable: List<Int>,
    current: Int,
    onMove: (Int) -> Unit,
    onEnter: (Int) -> Unit,
    onEscape: () -> Unit,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    if (selectable.isEmpty()) return e.key == Key.Escape && run { onEscape(); true }
    val pos = selectable.indexOf(current)
    when (e.key) {
        Key.DirectionDown -> onMove(selectable[if (pos < 0) 0 else (pos + 1) % selectable.size])
        Key.DirectionUp -> onMove(selectable[if (pos <= 0) selectable.lastIndex else pos - 1])
        Key.MoveHome -> onMove(selectable.first())
        Key.MoveEnd -> onMove(selectable.last())
        Key.Enter, Key.NumPadEnter -> if (current >= 0) onEnter(current) else return false
        Key.Escape -> onEscape()
        else -> return false
    }
    return true
}

/**
 * A non-editable combo box (`ComboBox.nonEditableBackground`, chevron, focus ring) with an
 * [IdePopup] list. Used for settings like "View: Standard".
 */
@Composable
fun <T> IdeDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    Box(modifier) {
        Row(
            Modifier
                .height(28.dp)
                .background(IdeColors.ControlBgRaised, shape)
                .border(if (open || focused) 2.dp else 1.dp, if (open || focused) IdeColors.FocusBorder else IdeColors.ControlBorder, shape)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null) { open = true }
                .padding(start = 9.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label(selected),
                style = IdeText.Default.copy(color = if (enabled) IdeColors.Text else IdeColors.TextDisabled),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.ChevronDown, alpha = if (enabled) 1f else 0.38f)
        }
        if (open) {
            IdeMenu(
                entries = options.map { option -> MenuItem(label(option), onClick = { onSelect(option) }, checked = option == selected) },
                onDismissRequest = { open = false },
                minWidth = 120.dp,
            )
        }
    }
}

/** A scrollable column capped at [maxHeight], for long popup lists. */
@Composable
fun PopupScrollColumn(maxHeight: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) { content() }
}
