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
package dev.logcatgui.retrace

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProguardRetracerTest {

    @Test
    fun deobfuscatesStackFrames() = runBlocking {
        val dir = createTempDirectory("logcat-gui-retrace").toFile()
        try {
            val mapping = File(dir, "mapping.txt").apply {
                writeText(
                    """
                    com.example.app.DetailFragment -> a.b:
                        1:1:void onLoaded(java.lang.String):88:88 -> a
                    com.example.app.Repository -> a.c:
                        1:1:java.lang.String load():12:12 -> b
                    """.trimIndent() + "\n"
                )
            }
            val retracer = ProguardRetracer.load(mapping.toPath())
            val result = retracer.retrace(
                "java.lang.IllegalStateException: boom\n\tat a.c.b(SourceFile:1)\n\tat a.b.a(SourceFile:1)"
            )
            assertTrue("com.example.app.Repository.load" in result, result)
            assertTrue("com.example.app.DetailFragment.onLoaded" in result, result)
            assertTrue("a.c.b(" !in result, result)
            assertEquals("mapping.txt", retracer.fileName)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun leavesOrdinaryMessagesAlone() = runBlocking {
        val dir = createTempDirectory("logcat-gui-retrace").toFile()
        try {
            val mapping = File(dir, "mapping.txt").apply { writeText("com.example.Foo -> a.a:\n") }
            val retracer = ProguardRetracer.load(mapping.toPath())
            assertEquals("Start proc 4321:com.example", retracer.retrace("Start proc 4321:com.example"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
