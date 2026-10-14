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

import dev.logcatgui.filter.LogcatFilter.Field
import dev.logcatgui.model.LogLevel
import java.util.regex.PatternSyntaxException

class FilterParseException(message: String) : Exception(message)

/**
 * Parses the Android Studio Logcat filter language:
 *
 * ```
 * query   := orExpr
 * orExpr  := andExpr ( '|' andExpr )*
 * andExpr := factor ( ['&'] factor )*    -- juxtaposition or '&' = AND, binds tighter than '|'
 * factor  := '(' orExpr ')' | term
 * term    := ['-' | '!'] [ key op ] value
 * op      := ':'  (contains)  |  '=:'  (exact)  |  '~:'  (regex)
 * value   := bareword | "quoted string"
 * ```
 *
 * Text comparisons ignore case unless [parse] is called with `matchCase = true` (the
 * filter box's **Cc** toggle). See docs/ARCHITECTURE.md §8 for the supported keys.
 */
object FilterParser {

    /** Keys that take text and support the `=:` and `~:` operators. */
    val TEXT_KEYS: Map<String, Field> = linkedMapOf(
        "tag" to Field.TAG,
        "package" to Field.PACKAGE,
        "app" to Field.PACKAGE,
        "process" to Field.PROCESS,
        "message" to Field.MESSAGE,
        "msg" to Field.MESSAGE,
        "line" to Field.LINE,
    )

    /** Every key, in the order completion offers them. */
    val ALL_KEYS = listOf("tag", "package", "process", "message", "line", "level", "age", "is", "pid", "tid")

    val IS_VALUES = listOf("crash", "stacktrace")

    fun parse(query: String, matchCase: Boolean = false): LogcatFilter {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return LogcatFilter.MatchAll
        val parser = Parser(tokens, ignoreCase = !matchCase)
        val result = parser.parseOr()
        if (!parser.atEnd()) throw FilterParseException("Unexpected '${parser.peekText()}'")
        return result
    }

    private sealed interface Token {
        data object LParen : Token
        data object RParen : Token
        data object Pipe : Token
        data class Term(val text: String) : Token
    }

    private fun tokenize(s: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() || c == '&' -> i++
                c == '(' -> { tokens += Token.LParen; i++ }
                c == ')' -> { tokens += Token.RParen; i++ }
                c == '|' -> { tokens += Token.Pipe; i++ }
                else -> {
                    val sb = StringBuilder()
                    while (i < s.length && !s[i].isWhitespace() && s[i] !in "()|&") {
                        if (s[i] == '"') {
                            val close = s.indexOf('"', i + 1)
                            if (close < 0) throw FilterParseException("Unterminated quote")
                            sb.append(s, i + 1, close)
                            i = close + 1
                        } else {
                            sb.append(s[i++])
                        }
                    }
                    tokens += Token.Term(sb.toString())
                }
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<Token>, private val ignoreCase: Boolean) {
        private var pos = 0

        fun atEnd() = pos >= tokens.size

        fun peekText(): String = when (val t = tokens[pos]) {
            Token.LParen -> "("
            Token.RParen -> ")"
            Token.Pipe -> "|"
            is Token.Term -> t.text
        }

        fun parseOr(): LogcatFilter {
            val parts = mutableListOf(parseAnd())
            while (!atEnd() && tokens[pos] == Token.Pipe) {
                pos++
                parts += parseAnd()
            }
            return parts.singleOrNull() ?: LogcatFilter.Or(parts)
        }

        private fun parseAnd(): LogcatFilter {
            val parts = mutableListOf<LogcatFilter>()
            while (!atEnd() && tokens[pos] != Token.Pipe && tokens[pos] != Token.RParen) {
                parts += parseFactor()
            }
            if (parts.isEmpty()) {
                throw FilterParseException(if (atEnd()) "Expected a term" else "Expected a term before '${peekText()}'")
            }
            return parts.singleOrNull() ?: LogcatFilter.And(parts)
        }

        private fun parseFactor(): LogcatFilter = when (val t = tokens[pos++]) {
            Token.LParen -> {
                val inner = parseOr()
                if (atEnd() || tokens[pos] != Token.RParen) throw FilterParseException("Missing ')'")
                pos++
                inner
            }
            is Token.Term -> parseTerm(t.text, ignoreCase)
            // parseAnd() never calls us on '|' or ')'.
            Token.Pipe, Token.RParen -> error("unreachable")
        }
    }

    private fun parseTerm(raw: String, ignoreCase: Boolean): LogcatFilter {
        var text = raw
        val negated = text.length > 1 && (text[0] == '-' || text[0] == '!')
        if (negated) text = text.substring(1)

        val colon = text.indexOf(':')
        val filter = if (colon > 0) {
            keyed(text.substring(0, colon).lowercase(), text.substring(colon + 1), ignoreCase)
                ?: LogcatFilter.FieldMatch(Field.MESSAGE, TextMatcher.Contains(text, ignoreCase))
        } else {
            LogcatFilter.FieldMatch(Field.MESSAGE, TextMatcher.Contains(text, ignoreCase))
        }
        return if (negated) LogcatFilter.Not(filter) else filter
    }

    /** Returns null for an unknown key so the caller can fall back to a message search. */
    private fun keyed(rawKey: String, value: String, ignoreCase: Boolean): LogcatFilter? {
        val op = rawKey.lastOrNull()?.takeIf { it == '=' || it == '~' }
        val key = if (op != null) rawKey.dropLast(1) else rawKey

        TEXT_KEYS[key]?.let { field ->
            if (field == Field.PACKAGE && op == null && value.equals("mine", ignoreCase = true)) {
                return LogcatFilter.PackageMine
            }
            val matcher = when (op) {
                '=' -> TextMatcher.Exact(value, ignoreCase)
                '~' -> try {
                    TextMatcher.Matches(value, ignoreCase)
                } catch (e: PatternSyntaxException) {
                    throw FilterParseException("Invalid regex '$value': ${e.description}")
                }
                else -> TextMatcher.Contains(value, ignoreCase)
            }
            return LogcatFilter.FieldMatch(field, matcher)
        }
        if (op != null) return null
        return when (key) {
            "level" -> LogcatFilter.LevelAtLeast(
                LogLevel.parse(value) ?: throw FilterParseException("Unknown level '$value'")
            )
            "pid" -> LogcatFilter.PidEquals(value.toIntOrNull() ?: throw FilterParseException("pid must be a number"))
            "tid" -> LogcatFilter.TidEquals(value.toIntOrNull() ?: throw FilterParseException("tid must be a number"))
            "age" -> LogcatFilter.AgeWithin(parseDuration(value))
            "is" -> when (value.lowercase()) {
                "crash" -> LogcatFilter.IsCrash
                "stacktrace" -> LogcatFilter.IsStacktrace
                else -> throw FilterParseException("Unknown 'is:' value '$value' (use ${IS_VALUES.joinToString(" or ")})")
            }
            else -> null
        }
    }

    /** `30s`, `5m`, `2h`, `1d` → milliseconds. */
    private fun parseDuration(value: String): Long {
        val m = Regex("""(\d+)([smhd])""").matchEntire(value.lowercase())
            ?: throw FilterParseException("age needs a number and a unit: s, m, h or d (e.g. age:5m)")
        val amount = m.groupValues[1].toLong()
        val unitMillis = when (m.groupValues[2]) {
            "s" -> 1_000L
            "m" -> 60_000L
            "h" -> 3_600_000L
            else -> 86_400_000L
        }
        return amount * unitMillis
    }
}
