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
package dev.logcatgui.filter

import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogcatHeader
import dev.logcatgui.model.LogcatMessage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterParserTest {

    private fun msg(
        tag: String = "Tag",
        message: String = "hello",
        level: LogLevel = LogLevel.INFO,
        app: String = "com.example.app",
        pid: Int = 100,
        tid: Int = 101,
    ) = LogcatMessage(LogcatHeader(level, pid, tid, app, app, tag, Instant.EPOCH), message)

    private fun matches(query: String, message: LogcatMessage = msg()) = FilterParser.parse(query).matches(message)

    @Test
    fun blankQueryMatchesEverything() {
        assertEquals(LogcatFilter.MatchAll, FilterParser.parse("   "))
    }

    @Test
    fun bareTermSearchesMessageCaseInsensitively() {
        assertTrue(matches("HELLO"))
        assertFalse(matches("bye"))
    }

    @Test
    fun keys() {
        assertTrue(matches("tag:ta"))
        assertTrue(matches("package:example"))
        assertTrue(matches("app:example"))
        assertTrue(matches("process:com.example"))
        assertTrue(matches("msg:ell"))
        assertTrue(matches("line:Tag"))
        assertTrue(matches("pid:100"))
        assertTrue(matches("tid:101"))
        assertFalse(matches("pid:7"))
    }

    @Test
    fun juxtapositionIsAnd() {
        assertTrue(matches("tag:Tag hello"))
        assertFalse(matches("tag:Tag bye"))
    }

    @Test
    fun pipeIsOr() {
        assertTrue(matches("tag:Other | hello"))
    }

    @Test
    fun andBindsTighterThanOr() {
        // "a | b c" means "a | (b c)"
        assertFalse(matches("tag:X | tag:Tag bye"))
        assertTrue(matches("tag:X | tag:Tag hello"))
    }

    @Test
    fun parenthesesGroup() {
        assertTrue(matches("(tag:X | tag:Tag) hello"))
        assertFalse(matches("(tag:X | tag:Y) hello"))
    }

    @Test
    fun negation() {
        assertFalse(matches("-tag:Tag"))
        assertTrue(matches("!tag:Other"))
    }

    @Test
    fun quotedValuesKeepSpaces() {
        assertTrue(matches("msg:\"hello world\"", msg(message = "say hello world")))
        assertTrue(matches("\"hello world\"", msg(message = "say hello world")))
        assertFalse(matches("\"hello world\"", msg(message = "hello there world")))
    }

    @Test
    fun levelIsAMinimum() {
        assertTrue(matches("level:info", msg(level = LogLevel.WARN)))
        assertTrue(matches("level:W", msg(level = LogLevel.WARN)))
        assertFalse(matches("level:E", msg(level = LogLevel.WARN)))
    }

    @Test
    fun packageMineMatchesAppLikeNames() {
        assertTrue(matches("package:mine", msg(app = "com.example")))
        assertFalse(matches("package:mine", msg(app = "surfaceflinger")))
    }

    @Test
    fun unknownKeyFallsBackToMessageSearch() {
        assertTrue(matches("http://x", msg(message = "GET http://x/y")))
    }

    @Test
    fun equivalentQueriesCompileToEqualFilters() {
        assertEquals(FilterParser.parse("tag:a  level:W"), FilterParser.parse(" tag:a level:W "))
    }

    @Test
    fun exactAndRegexOperators() {
        assertTrue(matches("tag=:tag"))
        assertFalse(matches("tag=:Ta"))
        assertTrue(matches("tag~:^T.g$"))
        assertFalse(matches("tag~:^x"))
        assertTrue(matches("-tag~:^x"))
        assertTrue(matches("package=:com.example.app"))
    }

    @Test
    fun matchCase() {
        assertTrue(FilterParser.parse("tag:tag").matches(msg()))
        assertFalse(FilterParser.parse("tag:tag", matchCase = true).matches(msg()))
        assertTrue(FilterParser.parse("tag:Tag", matchCase = true).matches(msg()))
        assertFalse(FilterParser.parse("tag~:^tag$", matchCase = true).matches(msg()))
    }

    @Test
    fun explicitAmpersandIsAnd() {
        assertTrue(matches("tag:Tag & hello"))
        assertFalse(matches("tag:Tag & bye"))
    }

    @Test
    fun age() {
        val now = 1_000_000_000L
        LogcatFilter.Clock.nowMillis = { now }
        try {
            val recent = LogcatMessage(LogcatHeader(LogLevel.INFO, 1, 1, "", "", "T", Instant.ofEpochMilli(now - 30_000)), "m")
            val old = LogcatMessage(LogcatHeader(LogLevel.INFO, 1, 1, "", "", "T", Instant.ofEpochMilli(now - 600_000)), "m")
            assertTrue(FilterParser.parse("age:1m").matches(recent))
            assertFalse(FilterParser.parse("age:1m").matches(old))
            assertTrue(FilterParser.parse("age:1h").matches(old))
        } finally {
            LogcatFilter.Clock.nowMillis = System::currentTimeMillis
        }
    }

    @Test
    fun isCrashAndStacktrace() {
        val crash = msg(tag = "AndroidRuntime", level = LogLevel.ERROR, message = "FATAL EXCEPTION: main\nProcess: com.example\njava.lang.RuntimeException: boom\n\tat com.example.Foo.bar(Foo.kt:42)")
        val native = msg(tag = "libc", level = LogLevel.ASSERT, message = "Fatal signal 11 (SIGSEGV), code 1")
        val warning = msg(tag = "AndroidRuntime", level = LogLevel.WARN, message = "FATAL EXCEPTION: main")
        assertTrue(matches("is:crash", crash))
        assertTrue(matches("is:crash", native))
        assertFalse(matches("is:crash", warning))
        assertTrue(matches("is:stacktrace", crash))
        assertFalse(matches("is:stacktrace", native))
    }

    @Test
    fun syntaxErrors() {
        for (bad in listOf("(tag:a", "tag:a)", "|", "a |", "level:loud", "pid:abc", "\"open", "tag~:(", "age:5", "age:soon", "is:bug")) {
            assertFailsWith<FilterParseException>(bad) { FilterParser.parse(bad) }
        }
    }
}
