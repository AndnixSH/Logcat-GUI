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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterCompletionTest {
    private val context = FilterCompletion.Context(
        tags = listOf("OkHttp", "ActivityManager", "My Tag"),
        packages = listOf("com.example.app", "com.google.android.gms"),
        processes = listOf("system_server"),
    )

    private fun labels(text: String, cursor: Int = text.length, ctx: FilterCompletion.Context = context) =
        FilterCompletion.suggest(text, cursor, ctx).map { it.label }

    @Test
    fun keysForAPrefix() {
        assertEquals(listOf("tag:", "tid:"), labels("t"))
        assertEquals(listOf("line:", "level:"), labels("l"))
    }

    @Test
    fun keysKeepNegation() {
        assertEquals(listOf("-tag:", "-tid:"), labels("-t"))
    }

    @Test
    fun valuesAfterAKey() {
        assertEquals(listOf("OkHttp"), labels("tag:ok"))
        assertTrue("mine" in labels("package:"))
        assertEquals(listOf("WARN"), labels("level:w"))
        assertEquals(listOf("crash", "stacktrace"), labels("is:c"), "prefix matches first, then substring matches")
    }

    @Test
    fun acceptingAValueReplacesOnlyTheCurrentToken() {
        val text = "level:error tag:Ac"
        val s = FilterCompletion.suggest(text, text.length, context).first()
        assertEquals("level:error tag:ActivityManager " to 32, s.applyTo(text))
    }

    @Test
    fun valuesWithSpacesAreQuoted() {
        val s = FilterCompletion.suggest("tag:My", 6, context).first()
        assertEquals("tag:\"My Tag\" ", s.replacement)
    }

    @Test
    fun completesInTheMiddleOfAQuery() {
        val text = "tag:Ok level:error"
        val s = FilterCompletion.suggest(text, 6, context).first()
        assertEquals("tag:OkHttp  level:error", s.applyTo(text).first)
    }

    @Test
    fun historyEntriesWhenEnabled() {
        val ctx = FilterCompletion.Context(history = listOf("tag:OkHttp level:debug", "package:com.example"))
        val suggestions = FilterCompletion.suggest("okhttp", 6, ctx).filter { it.hint == "history" }
        assertEquals(listOf("tag:OkHttp level:debug"), suggestions.map { it.label })
        assertEquals("tag:OkHttp level:debug" to 22, suggestions.single().applyTo("okhttp"))
    }
}
