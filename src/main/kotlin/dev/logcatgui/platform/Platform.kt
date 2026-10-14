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
package dev.logcatgui.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Desktop
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

private val osName: String = System.getProperty("os.name")
val isWindows: Boolean = osName.startsWith("Windows", ignoreCase = true)
val isMac: Boolean = osName.startsWith("Mac", ignoreCase = true)

/**
 * Native open/save dialogs (AWT `FileDialog`: the system dialog on Windows and macOS, the GTK
 * one on Linux).
 */
object FileDialogs {
    /** [extensions] (without dots) limit which files can be picked; empty means any file. */
    fun open(parent: Window?, title: String, directory: String?, extensions: List<String> = emptyList()): File? {
        val dialog = create(parent, title, FileDialog.LOAD, directory)
        if (extensions.isNotEmpty()) {
            if (isWindows) {
                // Windows ignores FilenameFilter; a wildcard in the file name acts as the filter.
                dialog.file = extensions.joinToString(";") { "*.$it" }
            } else {
                dialog.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
            }
        }
        return show(dialog)
    }

    fun save(parent: Window?, title: String, directory: String?, defaultName: String): File? {
        val dialog = create(parent, title, FileDialog.SAVE, directory)
        dialog.file = defaultName
        return show(dialog)
    }

    private fun create(parent: Window?, title: String, mode: Int, directory: String?): FileDialog {
        val dialog = when (parent) {
            is Frame -> FileDialog(parent, title, mode)
            is Dialog -> FileDialog(parent, title, mode)
            else -> FileDialog(null as Frame?, title, mode)
        }
        directory?.let { dialog.directory = it }
        return dialog
    }

    private fun show(dialog: FileDialog): File? {
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}

object Clipboard {
    fun copyText(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    fun copyPng(png: ByteArray) {
        val image = ImageIO.read(ByteArrayInputStream(png)) ?: return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
    }

    private class ImageSelection(private val image: Image) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}

object Shell {
    /** Opens [file] with its default app; returns false if the platform can't. */
    fun open(file: File): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        } else {
            // Linux desktops without GNOME libraries: fall back to the freedesktop opener.
            ProcessBuilder(if (isMac) "open" else "xdg-open", file.absolutePath).start()
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Shows [file] selected in Explorer / Finder, or opens its folder elsewhere. */
    fun reveal(file: File) {
        try {
            when {
                isWindows -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                isMac -> ProcessBuilder("open", "-R", file.absolutePath).start()
                else -> open(file.parentFile ?: file)
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * Gives a native window's title bar the IDE's colors on Windows 11 (DWM attributes), so it
 * matches the dark UI instead of the default light caption. No-op elsewhere or on failure.
 */
object WindowChrome {
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: DwmApi? by lazy {
        if (!isWindows) null else try {
            Native.load("dwmapi", DwmApi::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    fun applyDark(window: Window, caption: Color, text: Color, border: Color) {
        val api = dwm ?: return
        try {
            val hwnd = Native.getWindowPointer(window) ?: return
            api.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(1), 4)
            api.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, IntByReference(colorRef(caption)), 4)
            api.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, IntByReference(colorRef(text)), 4)
            api.DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, IntByReference(colorRef(border)), 4)
        } catch (_: Throwable) {
        }
    }

    /** Win32 COLORREF is 0x00BBGGRR. */
    private fun colorRef(color: Color): Int {
        val argb = color.toArgb()
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
