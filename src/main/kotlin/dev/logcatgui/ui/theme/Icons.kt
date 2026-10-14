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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import java.util.concurrent.ConcurrentHashMap

/**
 * Icon names under `/icons`. The SVGs are the dark variants of Android Studio's own icons:
 * IntelliJ Platform new-UI icons (JetBrains, Apache 2.0) and Android Studio artwork
 * (AOSP, Apache 2.0). See NOTICE.
 */
object Icons {
    const val Clear = "clear"
    const val Pause = "pause"
    const val Resume = "resume"
    const val Restart = "restart"
    const val ScrollToEnd = "scroll-to-end"
    const val SoftWrap = "soft-wrap"
    const val Import = "import"
    const val Export = "export"
    const val Format = "format"
    const val Split = "split"
    const val SplitRight = "split-right"
    const val SplitDown = "split-down"
    const val Proguard = "proguard"
    const val Screenshot = "screenshot"
    const val ScreenRecord = "screen-record"
    const val Add = "add"
    const val CloseSmall = "close-small"
    const val CloseSmallHovered = "close-small-hovered"
    const val ChevronDown = "chevron-down"
    const val Filter = "filter"
    const val FilterHistory = "filter-history"
    const val FavoriteOutline = "favorite-outline"
    const val FavoriteFilled = "favorite-filled"
    const val MatchCase = "match-case"
    const val Help = "help"
    const val Checked = "checked"
    const val Stop = "stop"
    const val Copy = "copy"
    const val Refresh = "refresh"
    const val Save = "save"
    const val MoreVertical = "more-vertical"
    const val Settings = "settings"
    const val Info = "info"
    const val DevicePhysical = "device-physical"
    const val DeviceVirtual = "device-virtual"
    const val TextFile = "text-file"
    const val AppIcon = "app-icon"

    private val doms = ConcurrentHashMap<String, SVGDOM>()

    fun dom(name: String): SVGDOM = doms.getOrPut(name) {
        val bytes = Icons::class.java.getResourceAsStream("/icons/$name.svg")?.use { it.readBytes() }
            ?: error("Missing icon /icons/$name.svg")
        SVGDOM(Data.makeFromBytes(bytes))
    }

    fun painter(name: String): Painter = SvgPainter(dom(name))
}

/** Draws an SVG scaled to the layout size. Skia renders it, as Compose's own SVG loader does. */
private class SvgPainter(private val dom: SVGDOM) : Painter() {
    private val viewBox = dom.root?.viewBox
    override val intrinsicSize: Size = Size(viewBox?.width ?: 16f, viewBox?.height ?: 16f)

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(size.width / intrinsicSize.width, size.height / intrinsicSize.height)
            dom.render(native)
            native.restore()
        }
    }
}

@Composable
fun Icon(name: String, modifier: Modifier = Modifier, size: Dp = 16.dp, alpha: Float = 1f) {
    val painter = remember(name) { Icons.painter(name) }
    Image(painter = painter, contentDescription = null, modifier = modifier.size(size), alpha = alpha)
}
