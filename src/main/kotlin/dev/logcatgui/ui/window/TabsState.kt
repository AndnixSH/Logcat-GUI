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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.logcatgui.AppServices
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.ui.panel.LogcatPanelState
import dev.logcatgui.ui.panel.PanelSource
import dev.logcatgui.ui.panel.SplitOrientation

/** A tab's layout: one panel, or two subtrees side by side / stacked (AS's "Split Panels"). */
sealed interface PanelNode

class LeafNode(val panel: LogcatPanelState) : PanelNode

class SplitNode(
    val orientation: SplitOrientation,
    val first: PanelNode,
    val second: PanelNode,
    ratio: Float = 0.5f,
) : PanelNode {
    /** Share of the space given to [first]; changed by dragging the divider. */
    var ratio by mutableFloatStateOf(ratio)
}

fun PanelNode.panels(): List<LogcatPanelState> = when (this) {
    is LeafNode -> listOf(panel)
    is SplitNode -> first.panels() + second.panels()
}

/** Replaces the leaf holding [target] with [replacement]; unchanged subtrees are reused. */
internal fun PanelNode.replace(target: LogcatPanelState, replacement: PanelNode): PanelNode = when (this) {
    is LeafNode -> if (panel === target) replacement else this
    is SplitNode -> {
        val f = first.replace(target, replacement)
        val s = second.replace(target, replacement)
        if (f === first && s === second) this else SplitNode(orientation, f, s, ratio)
    }
}

/** Removes the leaf holding [target]; its sibling takes the parent's place. Null if nothing is left. */
internal fun PanelNode.remove(target: LogcatPanelState): PanelNode? = when (this) {
    is LeafNode -> if (panel === target) null else this
    is SplitNode -> {
        val f = first.remove(target)
        val s = second.remove(target)
        when {
            f == null -> s
            s == null -> f
            f === first && s === second -> this
            else -> SplitNode(orientation, f, s, ratio)
        }
    }
}

class LogcatTab(val id: Int, title: String, root: PanelNode) {
    var title by mutableStateOf(title)
    var root by mutableStateOf(root)
}

/** The Logcat tool window's tabs ("Logcat", "Logcat (2)", …, or an imported file's name). */
class TabsState(private val services: AppServices) {
    val tabs = mutableStateListOf<LogcatTab>()
    var selectedIndex by mutableIntStateOf(0)
    private var nextId = 1

    val selected: LogcatTab? get() = tabs.getOrNull(selectedIndex)

    init {
        newTab()
    }

    /**
     * A new panel using the settings for new windows: "Default filter for new Logcat windows"
     * (or the most recently used filter) and the default view (Standard/Compact).
     */
    fun newPanel(
        source: PanelSource = PanelSource.DeviceSource(null),
        messages: List<LogcatMessage> = emptyList(),
        filter: String? = null,
    ): LogcatPanelState {
        val s = services.settings.settings
        val initialFilter = filter
            ?: s.lastFilter?.takeIf { s.logcat.mostRecentlyUsedFilterIsDefault }
            ?: s.logcat.defaultFilter
        return LogcatPanelState(services, source, initialFilter, s.format.defaultStyle, messages)
    }

    fun newTab(title: String = nextTitle(), panel: LogcatPanelState = newPanel()) {
        tabs += LogcatTab(nextId++, title, LeafNode(panel))
        selectedIndex = tabs.lastIndex
    }

    fun select(tab: LogcatTab) {
        tabs.indexOf(tab).takeIf { it >= 0 }?.let { selectedIndex = it }
    }

    fun close(tab: LogcatTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tab.root.panels().forEach { it.dispose() }
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            newTab()
            return
        }
        if (selectedIndex > index || selectedIndex > tabs.lastIndex) selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
    }

    fun closeOthers(tab: LogcatTab) {
        tabs.filter { it !== tab }.forEach { other ->
            other.root.panels().forEach { it.dispose() }
            tabs.remove(other)
        }
        selectedIndex = tabs.indexOf(tab).coerceAtLeast(0)
    }

    fun split(panel: LogcatPanelState, orientation: SplitOrientation) {
        val tab = tabOf(panel) ?: return
        val sibling = when (val source = panel.source) {
            is PanelSource.FileSource -> newPanel(source, panel.snapshotMessages(), panel.filterText)
            is PanelSource.DeviceSource -> newPanel(PanelSource.DeviceSource(source.serial))
        }
        tab.root = tab.root.replace(panel, SplitNode(orientation, LeafNode(panel), LeafNode(sibling)))
    }

    fun closePanel(panel: LogcatPanelState) {
        val tab = tabOf(panel) ?: return
        val remaining = tab.root.remove(panel) ?: return // never remove a tab's last panel this way
        panel.dispose()
        tab.root = remaining
    }

    fun canClosePanel(panel: LogcatPanelState): Boolean = tabOf(panel)?.root is SplitNode

    fun dispose() {
        tabs.forEach { tab -> tab.root.panels().forEach { it.dispose() } }
    }

    private fun tabOf(panel: LogcatPanelState): LogcatTab? = tabs.firstOrNull { tab -> tab.root.panels().any { it === panel } }

    private fun nextTitle(): String {
        val used = tabs.map { it.title }.toSet()
        if ("Logcat" !in used) return "Logcat"
        var n = 2
        while ("Logcat ($n)" in used) n++
        return "Logcat ($n)"
    }
}
