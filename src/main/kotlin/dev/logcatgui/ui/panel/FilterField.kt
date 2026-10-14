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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.logcatgui.LocalAppServices
import dev.logcatgui.filter.FilterCompletion
import dev.logcatgui.ui.components.ActionButton
import dev.logcatgui.ui.components.IdePopup
import dev.logcatgui.ui.components.PopupRow
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.Icon
import dev.logcatgui.ui.theme.Icons
import kotlinx.coroutines.delay

/**
 * Android Studio's Logcat filter box: history button, query field with completion
 * (Ctrl+Space, or automatically while typing), and the **Cc** match-case toggle.
 */
@Composable
fun FilterField(panel: LogcatPanelState, modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf(TextFieldValue(panel.filterText, TextRange(panel.filterText.length))) }
    var suggestions by remember { mutableStateOf(emptyList<FilterCompletion.Suggestion>()) }
    var highlighted by remember { mutableIntStateOf(0) }
    var showCompletion by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }

    // External changes (history pick, new panel) flow into the field.
    LaunchedEffect(panel.filterText) {
        if (panel.filterText != value.text) value = TextFieldValue(panel.filterText, TextRange(panel.filterText.length))
    }
    // Debounced compile while typing.
    LaunchedEffect(panel.filterText, panel.matchCase) {
        delay(150)
        panel.applyFilter()
    }
    // Leaving the field commits the filter to history, as pressing Enter does.
    LaunchedEffect(focused) {
        if (wasFocused && !focused) {
            showCompletion = false
            panel.commitFilter()
        }
        wasFocused = focused
    }

    fun refresh(v: TextFieldValue, force: Boolean) {
        val list = FilterCompletion.suggest(v.text, v.selection.end, panel.completionContext())
        suggestions = list
        highlighted = 0
        val typingToken = v.selection.end > 0 && !v.text[v.selection.end - 1].isWhitespace()
        showCompletion = list.isNotEmpty() && (force || typingToken)
    }

    fun accept(s: FilterCompletion.Suggestion) {
        val (text, cursor) = s.applyTo(value.text)
        value = TextFieldValue(text, TextRange(cursor))
        panel.filterText = text
        showCompletion = false
        if (s.replacement.endsWith(":")) refresh(value, force = true) // chain into values
    }

    val shape = RoundedCornerShape(4.dp)
    val error = panel.filterError != null
    Row(
        modifier
            .height(28.dp)
            .background(IdeColors.ControlBg, shape)
            .border(
                if (focused || error) 2.dp else 1.dp,
                when {
                    error -> IdeColors.ErrorBorder
                    focused -> IdeColors.FocusBorder
                    else -> IdeColors.ControlBorder
                },
                shape,
            )
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ActionButton(Icons.FilterHistory, "Show History", onClick = { historyOpen = true }, size = 24.dp)
            if (historyOpen) FilterHistoryPopup(panel, onDismiss = { historyOpen = false })
        }
        Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text("Press Ctrl+Space to see suggestions", style = IdeText.Default.copy(color = IdeColors.TextSecondary), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = { v ->
                    val textChanged = v.text != value.text
                    value = v
                    if (textChanged) {
                        panel.filterText = v.text
                        refresh(v, force = false)
                    } else if (showCompletion) {
                        refresh(v, force = true)
                    }
                },
                singleLine = true,
                textStyle = IdeText.Mono,
                cursorBrush = SolidColor(IdeColors.Text),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (e.key == Key.Spacebar && e.isCtrlPressed) {
                        refresh(value, force = true)
                        if (suggestions.isNotEmpty()) showCompletion = true
                        return@onPreviewKeyEvent true
                    }
                    if (showCompletion && suggestions.isNotEmpty()) {
                        when (e.key) {
                            Key.DirectionDown -> { highlighted = (highlighted + 1) % suggestions.size; return@onPreviewKeyEvent true }
                            Key.DirectionUp -> { highlighted = (highlighted - 1 + suggestions.size) % suggestions.size; return@onPreviewKeyEvent true }
                            Key.Enter, Key.NumPadEnter, Key.Tab -> { accept(suggestions[highlighted]); return@onPreviewKeyEvent true }
                            Key.Escape -> { showCompletion = false; return@onPreviewKeyEvent true }
                            else -> {}
                        }
                    }
                    if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                        panel.applyFilter()
                        panel.commitFilter()
                        return@onPreviewKeyEvent true
                    }
                    false
                },
            )
            if (showCompletion && focused && suggestions.isNotEmpty()) {
                CompletionPopup(suggestions, highlighted, onHover = { highlighted = it }, onAccept = ::accept, onDismiss = { showCompletion = false })
            }
        }
        ActionButton(
            Icons.MatchCase,
            "Match Case",
            onClick = { panel.matchCase = !panel.matchCase },
            selected = panel.matchCase,
            size = 24.dp,
        )
    }
}

@Composable
private fun CompletionPopup(
    suggestions: List<FilterCompletion.Suggestion>,
    highlighted: Int,
    onHover: (Int) -> Unit,
    onAccept: (FilterCompletion.Suggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(highlighted) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == highlighted } || visible.lastOrNull()?.index == highlighted) {
            listState.scrollToItem((highlighted - 4).coerceAtLeast(0))
        }
    }
    IdePopup(onDismissRequest = onDismiss, focusable = false, minWidth = 260.dp) {
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 288.dp).width(420.dp)) {
            itemsIndexed(suggestions) { i, s ->
                PopupRow(highlighted = i == highlighted, onHover = { onHover(i) }, onClick = { onAccept(s) }) {
                    Text(s.label, style = IdeText.Mono.copy(color = IdeColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    Text(s.hint, style = IdeText.Small.copy(color = IdeColors.TextSecondary), maxLines = 1)
                }
            }
        }
    }
}

/** Recent filters with favorite stars, like AS's filter history popup. */
@Composable
private fun FilterHistoryPopup(panel: LogcatPanelState, onDismiss: () -> Unit) {
    val store = LocalAppServices.current.settings
    val settings = store.settings
    val favorites = settings.favoriteFilters
    val recent = settings.filterHistory.filter { it !in favorites }

    fun use(filter: String) {
        onDismiss()
        panel.filterText = filter
        panel.applyFilter()
        panel.commitFilter()
    }

    fun toggleFavorite(filter: String) = store.update { s ->
        if (filter in s.favoriteFilters) s.copy(favoriteFilters = s.favoriteFilters - filter)
        else s.copy(favoriteFilters = s.favoriteFilters + filter)
    }

    IdePopup(onDismissRequest = onDismiss, minWidth = 320.dp) {
        Column(Modifier.widthIn(max = 640.dp)) {
            if (favorites.isEmpty() && recent.isEmpty()) {
                Text(
                    "No filter history yet. Filters are saved when you press Enter or leave the filter box.",
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp).widthIn(max = 360.dp),
                    style = IdeText.Secondary,
                )
            }
            if (favorites.isNotEmpty()) {
                SectionHeader("Favorites")
                favorites.forEach { HistoryRow(it, favorite = true, onUse = ::use, onStar = ::toggleFavorite) }
            }
            if (recent.isNotEmpty()) {
                SectionHeader("Recent")
                recent.forEach { HistoryRow(it, favorite = false, onUse = ::use, onStar = ::toggleFavorite) }
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).height(1.dp).background(IdeColors.Separator))
                var hovered by remember { mutableStateOf(false) }
                PopupRow(highlighted = hovered, onHover = { hovered = true }, onClick = {
                    store.update { it.copy(filterHistory = emptyList()) }
                    onDismiss()
                }) {
                    Text("Clear History", style = IdeText.Default)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 2.dp), style = IdeText.Small.copy(color = IdeColors.TextSecondary))
}

@Composable
private fun HistoryRow(filter: String, favorite: Boolean, onUse: (String) -> Unit, onStar: (String) -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    PopupRow(highlighted = hovered, onHover = { hovered = true }, onClick = { onUse(filter) }) {
        Box(
            Modifier
                .size(20.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onStar(filter) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (favorite) Icons.FavoriteFilled else Icons.FavoriteOutline)
        }
        Spacer(Modifier.width(6.dp))
        Text(filter, style = IdeText.Mono.copy(color = IdeColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The "?" popup: AS's filter syntax at a glance. */
@Composable
fun FilterHelpPopup(onDismiss: () -> Unit) {
    val rows = listOf(
        "timeout" to "message contains \"timeout\"",
        "tag:OkHttp" to "tag contains (also package:, process:, message:, line:)",
        "tag=:MyTag" to "tag is exactly MyTag",
        "tag~:^Net.*" to "tag matches a regular expression",
        "level:warn" to "Warn and above (V D I W E A)",
        "age:5m" to "logged in the last 5 minutes (s, m, h, d)",
        "is:crash  is:stacktrace" to "crashes / messages with stack frames",
        "pid:1234  tid:5678" to "a specific process or thread",
        "-tag:Choreographer" to "exclude (also !)",
        "a b   a & b" to "both",
        "a | b" to "either; ( ) groups",
        "\"two words\"" to "quotes keep spaces",
    )
    IdePopup(onDismissRequest = onDismiss, alignEnd = true) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).width(520.dp)) {
            Text("Logcat filter syntax", style = IdeText.Bold, modifier = Modifier.padding(bottom = 6.dp))
            rows.forEach { (query, meaning) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(query, style = IdeText.Mono.copy(color = IdeColors.Text), modifier = Modifier.width(190.dp))
                    Text(meaning, style = IdeText.Secondary)
                }
            }
            Text(
                "Press Ctrl+Space for completions. Cc makes text matching case-sensitive.",
                style = IdeText.Small.copy(color = IdeColors.TextSecondary),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
