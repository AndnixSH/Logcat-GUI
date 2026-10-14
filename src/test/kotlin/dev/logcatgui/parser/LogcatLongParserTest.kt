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
package dev.logcatgui.parser

import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogcatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogcatLongParserTest {

    private fun parseAll(vararg lines: String): List<LogcatMessage> {
        val parser = LogcatLongParser()
        val out = lines.mapNotNull { parser.process(it) }.toMutableList()
        parser.flush()?.let { out += it }
        return out
    }

    @Test
    fun parsesHeaderAndBody() {
        val parser = LogcatLongParser()
        assertNull(parser.process("[ 1760342096.123  1234: 1250 I/ActivityManager ]"))
        assertNull(parser.process("Start proc 4321"))
        val message = parser.process("")!!

        assertEquals(LogLevel.INFO, message.header.logLevel)
        assertEquals(1234, message.header.pid)
        assertEquals(1250, message.header.tid)
        assertEquals("ActivityManager", message.header.tag)
        assertEquals(1760342096123, message.header.timestamp.toEpochMilli())
        assertEquals("Start proc 4321", message.message)
    }

    @Test
    fun keepsMultiLineMessageTogether() {
        val messages = parseAll(
            "[ 1760342096.130  4321: 4321 E/AndroidRuntime ]",
            "FATAL EXCEPTION: main",
            "java.lang.RuntimeException: boom",
            "\tat com.example.Foo.bar(Foo.kt:42)",
            "",
        )
        assertEquals(1, messages.size)
        assertEquals(
            "FATAL EXCEPTION: main\njava.lang.RuntimeException: boom\n\tat com.example.Foo.bar(Foo.kt:42)",
            messages[0].message,
        )
    }

    @Test
    fun splitsOnHeaderWithoutBlankLine() {
        val messages = parseAll(
            "[ 1760342096.100  1: 1 D/A ]",
            "first",
            "[ 1760342096.200  2: 2 D/B ]",
            "second",
        )
        assertEquals(listOf("A" to "first", "B" to "second"), messages.map { it.header.tag to it.message })
    }

    @Test
    fun handlesPaddedIdsShortFractionsAndSpacesInTags() {
        val m = parseAll("[ 1760342096.5      1:    1 W/My Tag ]", "x", "").single()
        assertEquals(1, m.header.pid)
        assertEquals(1, m.header.tid)
        assertEquals("My Tag", m.header.tag)
        assertEquals(1760342096500, m.header.timestamp.toEpochMilli())
    }

    @Test
    fun mapsFatalToAssert() {
        val m = parseAll("[ 1760342096.000  9: 9 F/libc ]", "Fatal signal 11", "").single()
        assertEquals(LogLevel.ASSERT, m.header.logLevel)
    }

    @Test
    fun ignoresLinesOutsideMessages() {
        val messages = parseAll(
            "--------- beginning of main",
            "[ 1760342096.000  9: 9 I/T ]",
            "body",
            "",
            "--------- beginning of system",
        )
        assertEquals(listOf("body"), messages.map { it.message })
    }
}
