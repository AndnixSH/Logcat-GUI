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

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import dev.logcatgui.LocalAppServices
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.retrace.ProguardRetracer
import dev.logcatgui.settings.FormattingOptions
import dev.logcatgui.settings.LogcatSettings
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeFonts
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.LocalLogColors
import dev.logcatgui.ui.theme.LogColors

private val IdeScrollbar = ScrollbarStyle(
    minimalHeight = 24.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 300,
    unhoverColor = IdeColors.ScrollThumb,
    hoverColor = IdeColors.ScrollThumbHover,
)

private val ROW_PADDING = 8.dp
private val SCROLLBAR_GUTTER = 12.dp

/** Characters the message column needs before a wrapped line keeps its two-column layout. */
private const val MIN_MESSAGE_CHARS = 40

/**
 * The virtualized, colored, selectable log list.
 *
 * Like the AS editor: with soft-wrap off, lines keep their length and the view scrolls
 * horizontally; with soft-wrap on, messages wrap in the message column, or, when the panel
 * is too narrow for the header columns, whole lines wrap.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LogcatView(panel: LogcatPanelState, modifier: Modifier = Modifier) {
    val settings = LocalAppServices.current.settings.settings
    val options = settings.format.options(panel.formatStyle)
    val fontSize = if (settings.logcat.overrideFontSize) settings.logcat.fontSize else LogcatSettings.DEFAULT_FONT_SIZE
    // Trim.None keeps the full line height on first and last lines too, so a single-line row is
    // as tall as one line of a wrapped message and the spacing is uniform, as in an editor.
    val style = remember(fontSize) {
        TextStyle(
            fontFamily = IdeFonts.JetBrainsMono,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.35f).sp,
            color = IdeColors.EditorText,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth: Dp = remember(style, density) {
        with(density) { (measurer.measure("0".repeat(100), style).size.width / 100f).toDp() }
    }
    val headerWidth = charWidth * MessageFormatter.headerWidth(options)
    val colors = LocalLogColors.current
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()

    // Scrolling up pauses following the tail; reaching the bottom resumes it. New messages
    // grow the list without moving the viewport, so they never count as "moved up".
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isAtBottom())
        }.collect { (index, offset, atBottom) ->
            val movedUp = index < previousIndex || (index == previousIndex && offset < previousOffset)
            when {
                atBottom -> panel.followTail = true
                movedUp -> panel.followTail = false
            }
            previousIndex = index
            previousOffset = offset
        }
    }

    LaunchedEffect(panel.revision, panel.followTail, panel.softWrap, options, fontSize) {
        if (panel.followTail && panel.visible.isNotEmpty()) listState.scrollToItem(panel.visible.lastIndex)
    }

    BoxWithConstraints(modifier.background(IdeColors.EditorBg)) {
        val viewport = maxWidth - SCROLLBAR_GUTTER
        val wrap = panel.softWrap
        // Soft-wrap off: as wide as the longest line, scrolled horizontally.
        val contentWidth = if (wrap) viewport else {
            max(viewport, ROW_PADDING * 2 + headerWidth + charWidth * (panel.widestLine + 2))
        }
        val twoColumns = !wrap || headerWidth == 0.dp ||
            headerWidth + charWidth * (MIN_MESSAGE_CHARS + 1) + ROW_PADDING * 2 <= viewport

        SelectionContainer {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(end = SCROLLBAR_GUTTER)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight()
                        // Like AS: clicking a line stops auto-scrolling and keeps that line in view.
                        .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { panel.followTail = false },
                ) {
                    itemsIndexed(panel.visible) { index, message ->
                        val previous = if (index > 0) panel.visible.getOrNull(index - 1) else null
                        LogRow(message, previous, options, colors, panel.retracer, headerWidth, charWidth, style, wrap, twoColumns)
                    }
                }
            }
        }
        if (panel.visible.isEmpty()) {
            val hint = when {
                panel.totalCount > 0 -> "All ${panel.totalCount} messages are hidden by the filter"
                panel.paused -> "Paused"
                else -> null
            }
            if (hint != null) Text(hint, Modifier.align(Alignment.Center), style = IdeText.Secondary)
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp),
            style = IdeScrollbar,
        )
        if (!wrap && contentWidth > viewport) {
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontal),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 2.dp, end = SCROLLBAR_GUTTER, bottom = 2.dp),
                style = IdeScrollbar,
            )
        }
    }
}

@Composable
private fun LogRow(
    message: LogcatMessage,
    previous: LogcatMessage?,
    options: FormattingOptions,
    colors: LogColors,
    retracer: ProguardRetracer?,
    headerWidth: Dp,
    charWidth: Dp,
    style: TextStyle,
    wrap: Boolean,
    twoColumns: Boolean,
) {
    val usesPrevious = options.tag.hideDuplicates || options.appName.hideDuplicates || options.processName.hideDuplicates
    val header = remember(message, options, colors, if (usesPrevious) previous else null) {
        MessageFormatter.header(message, previous, options, colors)
    }
    val body = remember(message, retracer, colors) { MessageFormatter.body(message, retracer, colors) }
    if (!twoColumns) {
        // Too narrow for the header columns: wrap whole lines, as the AS editor does.
        val line = remember(header, body) { header + AnnotatedString(" ") + body }
        Text(line, Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING), style = style, softWrap = true)
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING)) {
        if (headerWidth > 0.dp) {
            Text(header, Modifier.widthIn(min = headerWidth), style = style, softWrap = false, maxLines = 1, overflow = TextOverflow.Clip)
        }
        Text(
            text = body,
            modifier = Modifier.weight(1f).padding(start = charWidth),
            style = style,
            // With wrap off, hard line breaks (stack traces) still break; the view scrolls sideways.
            softWrap = wrap,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun LazyListState.isAtBottom(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    return last.index >= info.totalItemsCount - 1
}
