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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.logcatgui.ui.theme.Icons

sealed interface ToolbarItem

/**
 * One toolbar action. With [menu], clicking opens a popup menu (like IntelliJ's action
 * groups); otherwise it runs [onClick]. [selected] renders a toggled-on button.
 */
data class ToolbarAction(
    val icon: String,
    val text: String,
    val onClick: () -> Unit = {},
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val shortcut: String? = null,
    val menu: (() -> List<MenuEntry>)? = null,
) : ToolbarItem

data object ToolbarSeparator : ToolbarItem

/**
 * A horizontal action toolbar that shows as many items as fit and moves the rest into a
 * trailing "more" menu, like IntelliJ's `ActionToolbar` in a narrow tool window.
 */
@Composable
fun OverflowToolbar(items: List<ToolbarItem>, modifier: Modifier = Modifier) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val measured: List<List<Placeable>> = items.mapIndexed { i, item ->
            subcompose(i) { ToolbarItemView(item) }.map { it.measure(loose) }
        }
        val widths = measured.map { list -> list.sumOf { it.width } }
        val total = widths.sum()
        var count = items.size
        var overflow: List<Placeable> = emptyList()
        if (constraints.hasBoundedWidth && total > constraints.maxWidth) {
            val overflowWidth = 28.dp.roundToPx()
            var used = 0
            count = 0
            while (count < items.size && used + widths[count] + overflowWidth <= constraints.maxWidth) {
                used += widths[count]
                count++
            }
            while (count > 0 && items[count - 1] is ToolbarSeparator) count--
            val hidden = items.drop(count)
            overflow = subcompose("overflow") { OverflowButton(hidden) }.map { it.measure(loose) }
        }
        val shown = measured.take(count).flatten() + overflow
        val height = shown.maxOfOrNull { it.height } ?: 0
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else shown.sumOf { it.width }
        layout(width, height) {
            var x = 0
            for (p in shown) {
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width
            }
        }
    }
}

@Composable
private fun ToolbarItemView(item: ToolbarItem) {
    when (item) {
        ToolbarSeparator -> ToolbarSeparatorLine()
        is ToolbarAction -> {
            var open by remember { mutableStateOf(false) }
            Box {
                ActionButton(
                    icon = item.icon,
                    tooltip = item.text,
                    shortcut = item.shortcut,
                    selected = item.selected,
                    enabled = item.enabled,
                    onClick = { if (item.menu != null) open = true else item.onClick() },
                )
                if (open && item.menu != null) {
                    IdeMenu(item.menu.invoke(), onDismissRequest = { open = false })
                }
            }
        }
    }
}

@Composable
private fun OverflowButton(hidden: List<ToolbarItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionButton(Icons.MoreVertical, tooltip = "More", onClick = { open = true })
        if (open) IdeMenu(overflowEntries(hidden), onDismissRequest = { open = false })
    }
}

/** Hidden actions as menu rows; an action group becomes a header followed by its items. */
private fun overflowEntries(hidden: List<ToolbarItem>): List<MenuEntry> = buildList {
    for (item in hidden) {
        when (item) {
            ToolbarSeparator -> if (isNotEmpty() && last() != MenuSeparator) add(MenuSeparator)
            is ToolbarAction -> {
                val menu = item.menu
                if (menu == null) {
                    add(MenuItem(item.text, item.onClick, icon = item.icon, checked = item.selected, enabled = item.enabled, trailing = item.shortcut))
                } else {
                    if (isNotEmpty() && last() != MenuSeparator) add(MenuSeparator)
                    add(MenuHeader(item.text))
                    addAll(menu())
                }
            }
        }
    }
    while (isNotEmpty() && last() == MenuSeparator) removeAt(lastIndex)
}
