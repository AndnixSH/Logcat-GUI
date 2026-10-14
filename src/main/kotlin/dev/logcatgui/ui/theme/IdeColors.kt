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
package dev.logcatgui.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors of the IntelliJ "Islands Dark" UI theme (the Android Studio default), taken from
 * the palette and semantic tokens in its `ManyIslandsDark.theme.json`. Token names are kept
 * in the comments so values can be re-synced with a newer IDE.
 *
 * The log area uses the "Dark" editor color scheme (which inherits Darcula).
 */
object IdeColors {
    // Palette
    private val Gray10 = Color(0xFF191A1C)
    private val Gray20 = Color(0xFF212326)
    private val Gray30 = Color(0xFF26282C)
    private val Gray40 = Color(0xFF33353B)
    private val Gray50 = Color(0xFF40434A)
    private val Gray60 = Color(0xFF4C4F56)
    private val Gray70 = Color(0xFF5F6269)
    private val Gray80 = Color(0xFF73767C)
    private val Gray100 = Color(0xFF9FA2A8)
    private val Gray120 = Color(0xFFC3C5CB)
    private val Gray130 = Color(0xFFD1D3D9)
    private val Blue40 = Color(0xFF233558)
    private val Blue50 = Color(0xFF2A4371)
    private val Blue60 = Color(0xFF2E4D89)
    private val Blue80 = Color(0xFF3871E1)
    private val Blue100 = Color(0xFF71A1FE)
    private val Red80 = Color(0xFFC54E58)
    private val Red100 = Color(0xFFF57E84)
    private val Yellow100 = Color(0xFFD59637)
    private val Green100 = Color(0xFF6DB083)

    // Surfaces
    val ToolWindowBg = Gray10            // tool-window-bg
    val ToolWindowBgAlt = Gray20         // tool-window-bg-alt
    val PanelBorder = Gray30             // tool-window-border
    val DialogBg = Gray10                // dialog-bg
    val MainWindowBg = Gray30            // main-window-bg (status bar)
    val PopupBg = Gray30                 // popup-bg
    val PopupBorder = Gray40             // popup-border
    val TooltipBg = Gray40               // feedback-bg
    val TooltipBorder = Gray50

    // Text
    val Text = Gray130                   // text-default
    val TextMuted = Gray100              // text-muted
    val TextSecondary = Gray80           // text-secondary (*.infoForeground)
    val TextDisabled = Gray60            // text-disabled
    val TextLink = Blue100               // text-link
    val TextError = Red100               // text-error
    val TextWarning = Yellow100          // text-warning
    val TextSuccess = Green100           // text-success
    val TextOverAccent = Color.White     // text-over-accent

    // Controls
    val ControlBg = Gray10               // control-bg (text fields)
    val ControlBgRaised = Gray30         // control-bg-raised (non-editable combo box)
    val ControlBorder = Gray50           // control-border
    val ControlBorderRaised = Gray70     // control-border-raised
    val ControlBorderDisabled = Gray40   // control-border-disabled
    val FocusBorder = Blue80             // control-brand-border (focus ring)
    val ErrorBorder = Red80              // control-error-border
    val ButtonDefaultBg = Blue80         // control-brand-bg
    val CheckboxSelected = Blue80

    // Selection, hover, toolbar
    val SelectionBg = Blue50             // selection-bg-active
    val SelectionBgMuted = Blue40        // selection-bg-active-muted
    val ListHoverBg = Color(0x10FFFFFF)  // selection-bg-hovered (transparent-white-10)
    val ToolbarHoverBg = Color(0x17FFFFFF)   // toolbar-bg-hovered (transparent-white-20)
    val ToolbarPressedBg = Color(0x29FFFFFF) // toolbar-bg-pressed (transparent-white-40)
    val ToolbarSelectedBg = Blue60       // toolbar-selected-bg
    val TabSelectedBg = Blue40           // tab-selected-bg-active
    val TabHoverBg = Color(0x17FFFFFF)   // tab-bg-hovered
    val Separator = Gray40               // *.separatorColor
    val Icon = Color(0xFFCED0D6)         // icon stroke baked into the _dark SVGs
    val IconDefault = Gray120            // icon-default-stroke

    // Scrollbar (ScrollBar.thumbColor / hoverThumbColor)
    val ScrollThumb = Color(0x59808080)
    val ScrollThumbHover = Color(0x8C808080)

    // Editor ("Dark" scheme → Darcula)
    val EditorBg = Color(0xFF1E1F22)     // TEXT background
    val EditorText = Color(0xFFBCBEC4)   // TEXT foreground / editor-text
    val EditorSelection = Color(0xFF214283) // SELECTION_BACKGROUND (Darcula)
    val EditorHyperlink = Color(0xFF548AF7) // HYPERLINK_ATTRIBUTES
    val EditorSearchMatch = Color(0xFF114957) // TEXT_SEARCH_RESULT_ATTRIBUTES
}
