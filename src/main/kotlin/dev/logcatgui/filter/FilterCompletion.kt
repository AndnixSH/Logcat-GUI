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

/**
 * Completion for the filter box (Ctrl+Space, or automatically while typing):
 *
 * - while typing a key → matching keys (`tag:`, `level:`, …), keeping a leading `-`;
 * - after `key:` → values: tags/packages/processes seen in this panel, levels, `is:` kinds, ages;
 * - with "Add filters from history to autocomplete" on → history entries matching the text.
 */
object FilterCompletion {

    data class Suggestion(
        /** Shown in the list. */
        val label: String,
        /** Text that replaces [from]..[to] of the query. */
        val replacement: String,
        val from: Int,
        val to: Int,
        /** Muted hint on the right ("key", "tag", "history", …). */
        val hint: String,
    ) {
        fun applyTo(text: String): Pair<String, Int> {
            val updated = text.substring(0, from) + replacement + text.substring(to)
            return updated to from + replacement.length
        }
    }

    class Context(
        val tags: Collection<String> = emptyList(),
        val packages: Collection<String> = emptyList(),
        val processes: Collection<String> = emptyList(),
        val history: List<String> = emptyList(),
    )

    private const val LIMIT = 50
    private val AGES = listOf("30s", "1m", "5m", "15m", "1h", "1d")

    fun suggest(text: String, cursor: Int, context: Context): List<Suggestion> {
        val c = cursor.coerceIn(0, text.length)
        var start = c
        while (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] !in "()|&") start--
        val token = text.substring(start, c)
        val result = mutableListOf<Suggestion>()

        val negation = token.takeWhile { it == '-' || it == '!' }.take(1)
        val body = token.drop(negation.length)
        val colon = body.indexOf(':')
        if (colon > 0) {
            val rawKey = body.substring(0, colon)
            val key = rawKey.trimEnd('=', '~').lowercase()
            val prefix = body.substring(colon + 1).removePrefix("\"")
            val values: Collection<String> = when (key) {
                "tag" -> context.tags
                "package", "app" -> listOf("mine") + context.packages
                "process" -> context.processes
                "level" -> LogLevel.entries.map { it.name }
                "is" -> FilterParser.IS_VALUES
                "age" -> AGES
                else -> emptyList()
            }
            for (value in rank(values, prefix)) {
                val quoted = if (value.any { it.isWhitespace() }) "\"$value\"" else value
                result += Suggestion(value, "$negation$rawKey:$quoted ", start, c, key)
                if (result.size >= LIMIT) break
            }
        } else {
            for (key in FilterParser.ALL_KEYS) {
                if (key.startsWith(body, ignoreCase = true) && key != body) {
                    result += Suggestion("$negation$key:", "$negation$key:", start, c, "key")
                }
            }
        }

        if (context.history.isNotEmpty()) {
            val whole = text.trim()
            for (entry in context.history) {
                if (result.size >= LIMIT) break
                if (entry != whole && (whole.isEmpty() || entry.contains(whole, ignoreCase = true))) {
                    result += Suggestion(entry, entry, 0, text.length, "history")
                }
            }
        }
        return result
    }

    /** Prefix matches first (in their original order), then other substring matches. */
    private fun rank(values: Collection<String>, prefix: String): List<String> {
        val distinct = values.distinct()
        if (prefix.isEmpty()) return distinct.take(LIMIT)
        val starts = distinct.filter { it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true) }
        val contains = distinct.filter { !it.startsWith(prefix, ignoreCase = true) && it.contains(prefix, ignoreCase = true) }
        return (starts + contains).take(LIMIT)
    }
}
