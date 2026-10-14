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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.Icon

/** IntelliJ-style tooltip: `ToolTip.background` with an optional muted shortcut. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tooltip(text: String?, shortcut: String? = null, content: @Composable () -> Unit) {
    if (text == null) {
        content()
        return
    }
    TooltipArea(
        tooltip = {
            val shape = RoundedCornerShape(6.dp)
            Row(
                modifier = Modifier
                    .background(IdeColors.TooltipBg, shape)
                    .border(1.dp, IdeColors.TooltipBorder, shape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text, style = IdeText.Default)
                if (shortcut != null) Text(shortcut, style = IdeText.Default.copy(color = IdeColors.TextMuted))
            }
        },
        delayMillis = 700,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
        content = content,
    )
}

/**
 * A toolbar icon button (`ActionButton`): transparent until hovered, `pressedBackground`
 * while pressed, and the same pressed look while [selected] (a toggled-on action).
 */
@Composable
fun ActionButton(
    icon: String,
    tooltip: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 28.dp,
    shortcut: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(6.dp)
    Tooltip(tooltip, shortcut) {
        Box(
            modifier = modifier
                .size(size)
                .background(
                    when {
                        !enabled -> Color.Transparent
                        pressed || selected -> IdeColors.ToolbarPressedBg
                        hovered -> IdeColors.ToolbarHoverBg
                        else -> Color.Transparent
                    },
                    shape,
                )
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, alpha = if (enabled) 1f else 0.38f)
        }
    }
}

/** Vertical separator between toolbar groups. */
@Composable
fun ToolbarSeparatorLine(height: Dp = 20.dp) {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(height).background(IdeColors.Separator))
}

@Composable
fun HorizontalLine(modifier: Modifier = Modifier, color: Color = IdeColors.Separator) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Dialog button. [default] is the accent (Enter) button, like `Button.default.*`. */
@Composable
fun IdeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, default: Boolean = false, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    val bg = when {
        !enabled -> Color.Transparent
        default -> if (hovered) IdeColors.ButtonDefaultBg.copy(alpha = 0.9f) else IdeColors.ButtonDefaultBg
        hovered -> IdeColors.ToolbarHoverBg
        else -> Color.Transparent
    }
    val border = when {
        focused && enabled -> IdeColors.FocusBorder
        default && enabled -> IdeColors.ButtonDefaultBg
        enabled -> IdeColors.ControlBorder
        else -> IdeColors.ControlBorderDisabled
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 72.dp)
            .height(28.dp)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IdeText.Default.copy(
                color = when {
                    !enabled -> IdeColors.TextDisabled
                    default -> IdeColors.TextOverAccent
                    else -> IdeColors.Text
                },
            ),
        )
    }
}

/** A 16dp checkbox with an optional label; the whole row is clickable. */
@Composable
fun IdeCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String? = null, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(3.dp)
        Box(
            Modifier
                .size(16.dp)
                .background(if (checked && enabled) IdeColors.CheckboxSelected else IdeColors.ControlBg, shape)
                .border(1.dp, if (checked && enabled) IdeColors.CheckboxSelected else IdeColors.ControlBorderRaised, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Canvas(Modifier.size(10.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.52f)
                        lineTo(size.width * 0.38f, size.height * 0.82f)
                        lineTo(size.width * 0.92f, size.height * 0.2f)
                    }
                    drawPath(path, if (enabled) Color.White else IdeColors.TextDisabled, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = IdeText.Default.copy(color = if (enabled) IdeColors.Text else IdeColors.TextDisabled))
        }
    }
}

@Composable
fun IdeRadioButton(selected: Boolean, onClick: () -> Unit, label: String, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val r = size.minDimension / 2
            if (selected && enabled) {
                drawCircle(IdeColors.CheckboxSelected, r)
                drawCircle(Color.White, r * 0.36f)
            } else {
                drawCircle(IdeColors.ControlBg, r)
                drawCircle(IdeColors.ControlBorderRaised, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                if (selected) drawCircle(IdeColors.TextDisabled, r * 0.36f, center = Offset(r, r))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = IdeText.Default.copy(color = if (enabled) IdeColors.Text else IdeColors.TextDisabled))
    }
}

/** Single-line text field: `TextField.background`, 1dp border, 2dp focus ring, red on [error]. */
@Composable
fun IdeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = IdeText.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = if (enabled) textStyle.color else IdeColors.TextDisabled),
        cursorBrush = SolidColor(IdeColors.Text),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(
                Modifier
                    .height(28.dp)
                    .background(IdeColors.ControlBg, shape)
                    .border(
                        if (focused || error) 2.dp else 1.dp,
                        when {
                            error -> IdeColors.ErrorBorder
                            focused -> IdeColors.FocusBorder
                            enabled -> IdeColors.ControlBorder
                            else -> IdeColors.ControlBorderDisabled
                        },
                        shape,
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = textStyle.copy(color = IdeColors.TextSecondary), maxLines = 1)
                }
                inner()
            }
        },
    )
}

@Composable
fun IdeLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        style = IdeText.Default.copy(
            color = IdeColors.TextLink,
            textDecoration = if (hovered) TextDecoration.Underline else null,
        ),
    )
}
