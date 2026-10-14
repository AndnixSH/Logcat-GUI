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
package dev.logcatgui.tools

import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders `icons/app-icon.svg` into the installer icon formats (`./gradlew generateIcons`):
 * `app.ico` (Windows, 16–256 px), `app.icns` (macOS, 16–1024 px, inset to Apple's icon
 * grid) and `app.png` (Linux, 512 px). The outputs are committed under `packaging/icons`.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "packaging/icons").apply { mkdirs() }
    val svg = object {}.javaClass.getResourceAsStream("/icons/app-icon.svg")!!.use { it.readBytes() }

    File(out, "app.png").writeBytes(render(svg, 512))

    File(out, "app.ico").writeBytes(ico(listOf(16, 24, 32, 48, 64, 128, 256).map { it to render(svg, it) }))

    // macOS icons leave a margin around the artwork (an 824 px body on a 1024 px canvas).
    val macInset = 0.056f
    File(out, "app.icns").writeBytes(
        icns(
            listOf(
                "icp4" to 16, "icp5" to 32, "icp6" to 64, "ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024,
                "ic11" to 32, "ic12" to 64, "ic13" to 256, "ic14" to 512,
            ).map { (type, size) -> type to render(svg, size, macInset) }
        )
    )
    println("Wrote app.png, app.ico, app.icns to ${out.absolutePath}")
}

private fun render(svg: ByteArray, size: Int, inset: Float = 0f): ByteArray {
    val dom = SVGDOM(Data.makeFromBytes(svg))
    val box = dom.root?.viewBox ?: error("app-icon.svg needs a viewBox")
    val surface = Surface.makeRasterN32Premul(size, size)
    val canvas = surface.canvas
    canvas.clear(0)
    val content = size * (1 - 2 * inset)
    canvas.translate(size * inset, size * inset)
    canvas.scale(content / box.width, content / box.height)
    dom.render(canvas)
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}

/** An ICO file with PNG-compressed entries (supported since Windows Vista). */
private fun ico(images: List<Pair<Int, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    fun le16(v: Int) {
        out.write(v and 0xFF)
        out.write(v shr 8 and 0xFF)
    }
    fun le32(v: Int) {
        le16(v and 0xFFFF)
        le16(v ushr 16)
    }
    le16(0) // reserved
    le16(1) // type: icon
    le16(images.size)
    var offset = 6 + 16 * images.size
    for ((size, png) in images) {
        out.write(if (size >= 256) 0 else size) // 0 means 256
        out.write(if (size >= 256) 0 else size)
        out.write(0) // palette
        out.write(0) // reserved
        le16(1) // planes
        le16(32) // bits per pixel
        le32(png.size)
        le32(offset)
        offset += png.size
    }
    images.forEach { out.write(it.second) }
    return out.toByteArray()
}

/** An ICNS file with PNG entries (big-endian `type, length` chunks). */
private fun icns(entries: List<Pair<String, ByteArray>>): ByteArray {
    fun be32(out: ByteArrayOutputStream, v: Int) {
        out.write(v ushr 24 and 0xFF)
        out.write(v ushr 16 and 0xFF)
        out.write(v ushr 8 and 0xFF)
        out.write(v and 0xFF)
    }
    val body = ByteArrayOutputStream()
    for ((type, png) in entries) {
        body.write(type.toByteArray(Charsets.US_ASCII))
        be32(body, png.size + 8)
        body.write(png)
    }
    val out = ByteArrayOutputStream()
    out.write("icns".toByteArray(Charsets.US_ASCII))
    be32(out, body.size() + 8)
    body.writeTo(out)
    return out.toByteArray()
}
