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
package dev.logcatgui.files

import dev.logcatgui.adb.Device
import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogcatHeader
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.parser.LogcatLongParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The contents of an imported log file. */
data class LogcatFileData(
    val messages: List<LogcatMessage>,
    /** Device description from the file's metadata, e.g. "samsung SM-X710 Android 16, API 36.1". */
    val deviceDescription: String? = null,
    /** The filter that was active when the file was exported. */
    val filter: String? = null,
)

/**
 * Reads and writes Android Studio's `.logcat` export format (pretty-printed JSON written
 * by AS's Gson-based `LogcatFileIo`), so files move freely between this app and Studio:
 *
 * ```json
 * { "metadata": { "device": { "physicalDevice": { … } }, "filter": "…", "projectApplicationIds": [] },
 *   "logcatMessages": [ { "header": { "logLevel": "INFO", "pid": 1, "tid": 1, "applicationId": "…",
 *       "processName": "…", "tag": "…", "timestamp": { "seconds": 0, "nanos": 0 } }, "message": "…" } ] }
 * ```
 *
 * Import also accepts plain text: `adb logcat -v threadtime` (also with `-v uid`), bugreport
 * text (the logcat sections are picked out), Firebase Test Lab logs, and this app's own
 * `-v long -v epoch` captures.
 */
object LogcatFiles {
    const val EXTENSION = "logcat"

    // Two-space indent, as Gson's pretty printer (which AS uses) writes it.
    @OptIn(ExperimentalSerializationApi::class)
    private val JSON = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(file: File, messages: List<LogcatMessage>, device: Device?, filter: String) {
        val data = FileJson(
            metadata = MetadataJson(device = device?.let(::deviceJson), filter = filter),
            logcatMessages = messages.map { m ->
                MessageJson(
                    header = HeaderJson(
                        logLevel = m.header.logLevel.name,
                        pid = m.header.pid,
                        tid = m.header.tid,
                        applicationId = m.header.applicationId,
                        processName = m.header.processName,
                        tag = m.header.tag,
                        timestamp = TimestampJson(m.header.timestamp.epochSecond, m.header.timestamp.nano),
                    ),
                    message = m.message,
                )
            },
        )
        file.writeText(JSON.encodeToString(FileJson.serializer(), data))
    }

    fun import(file: File): LogcatFileData {
        val text = file.readText()
        if (text.trimStart().startsWith("{")) return importJson(text)
        val messages = parseText(text.lineSequence(), year = LocalDateTime.now().year)
        if (messages.isEmpty()) throw IOException("No logcat messages found in ${file.name}.")
        return LogcatFileData(messages)
    }

    internal fun importJson(text: String): LogcatFileData {
        val data = try {
            JSON.decodeFromString(FileJson.serializer(), text)
        } catch (e: SerializationException) {
            throw IOException("Not a valid .logcat file: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw IOException("Not a valid .logcat file: ${e.message}")
        }
        val messages = data.logcatMessages.map { m ->
            LogcatMessage(
                LogcatHeader(
                    logLevel = LogLevel.entries.firstOrNull { it.name == m.header.logLevel } ?: LogLevel.VERBOSE,
                    pid = m.header.pid,
                    tid = m.header.tid,
                    applicationId = m.header.applicationId,
                    processName = m.header.processName,
                    tag = m.header.tag,
                    timestamp = Instant.ofEpochSecond(m.header.timestamp.seconds, m.header.timestamp.nanos.toLong()),
                ),
                m.message,
            )
        }
        return LogcatFileData(messages, describeDevice(data.metadata?.device), data.metadata?.filter?.takeIf { it.isNotBlank() })
    }

    /** Parses text logs; consecutive lines with identical headers merge into one message, as in AS. */
    internal fun parseText(lines: Sequence<String>, year: Int, zone: ZoneId = ZoneId.systemDefault()): List<LogcatMessage> {
        val messages = mutableListOf<LogcatMessage>()
        val longParser = LogcatLongParser()
        var pending: LogcatHeader? = null
        val body = StringBuilder()

        fun flush() {
            pending?.let { messages += LogcatMessage(it, body.toString()) }
            pending = null
            body.setLength(0)
        }

        for (line in lines) {
            val parsed = parseTextLine(line, year, zone)
            if (parsed != null) {
                val (header, text) = parsed
                if (header == pending) {
                    body.append('\n').append(text)
                } else {
                    flush()
                    pending = header
                    body.append(text)
                }
                continue
            }
            longParser.process(line)?.let { flush(); messages += it }
        }
        flush()
        longParser.flush()?.let { messages += it }
        return messages
    }

    private val THREADTIME = Regex(
        """^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d\d\d) +(?:\S+ +)?(\d+) +(\d+) ([VDIWEAF]) (.+?): (.*)$""",
    )
    private val FIREBASE = Regex(
        """^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d\d\d): ([VDIWEAF])/(.+?)\((\s*\d+)\): (.*)$""",
    )

    private fun parseTextLine(line: String, year: Int, zone: ZoneId): Pair<LogcatHeader, String>? {
        THREADTIME.matchEntire(line)?.let { m ->
            val g = m.groupValues
            val time = timestamp(year, g[1], g[2], g[3], g[4], g[5], g[6], zone) ?: return null
            val header = LogcatHeader(
                LogLevel.fromLetter(g[9][0]) ?: LogLevel.VERBOSE, g[7].toInt(), g[8].toInt(), "", "", g[10].trimEnd(), time,
            )
            return header to g[11]
        }
        FIREBASE.matchEntire(line)?.let { m ->
            val g = m.groupValues
            val time = timestamp(year, g[1], g[2], g[3], g[4], g[5], g[6], zone) ?: return null
            val pid = g[9].trim().toInt()
            val header = LogcatHeader(LogLevel.fromLetter(g[7][0]) ?: LogLevel.VERBOSE, pid, pid, "", "", g[8], time)
            return header to g[10]
        }
        return null
    }

    private fun timestamp(year: Int, mo: String, d: String, h: String, mi: String, s: String, ms: String, zone: ZoneId): Instant? =
        try {
            LocalDateTime.of(year, mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt(), ms.toInt() * 1_000_000)
                .atZone(zone).toInstant()
        } catch (_: java.time.DateTimeException) {
            null
        }

    /** AS's `Device` JSON: `{"physicalDevice": {…}}` or `{"emulatorDevice": {…}}`. */
    private fun deviceJson(device: Device): JsonObject = buildJsonObject {
        val major = device.sdkMajor ?: 0
        val minor = device.apiLevel?.substringAfter('.', "0")?.toIntOrNull() ?: 0
        val key = if (device.isEmulator) "emulatorDevice" else "physicalDevice"
        putJsonObject(key) {
            putJsonObject("deviceId") {
                put("pluginId", if (device.isEmulator) "LocalEmulator" else "PhysicalDevice")
                put("isTemplate", false)
                put("identifier", "serial=${device.serial}")
            }
            put("serialNumber", device.serial)
            put("isOnline", device.isOnline)
            put("release", device.release ?: "")
            putJsonObject("apiLevel") {
                put("majorVersion", major)
                put("minorVersion", minor)
            }
            put("featureLevel", major)
            if (device.isEmulator) {
                put("avdName", device.name)
                put("avdPath", "")
            } else {
                put("manufacturer", device.manufacturer ?: "")
                put("model", device.model ?: device.name)
            }
            put("type", "HANDHELD")
        }
    }

    private fun describeDevice(json: JsonObject?): String? {
        val inner = json?.let { it["physicalDevice"] ?: it["emulatorDevice"] }?.jsonObject ?: return null
        fun str(key: String) = (inner[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val name = str("avdName") ?: listOfNotNull(str("manufacturer"), str("model")).joinToString(" ").ifBlank { null }
            ?: str("serialNumber") ?: return null
        val api = inner["apiLevel"]?.let { (it as? JsonObject) }?.let { level ->
            val major = level["majorVersion"]?.jsonPrimitive?.intOrNull
            val minor = level["minorVersion"]?.jsonPrimitive?.intOrNull ?: 0
            major?.let { if (minor > 0) "$it.$minor" else "$it" }
        }
        val details = listOfNotNull(str("release")?.let { "Android $it" }, api?.let { "API $it" }).joinToString(", ")
        return if (details.isEmpty()) name else "$name $details"
    }

    @Serializable
    private data class FileJson(val metadata: MetadataJson? = null, val logcatMessages: List<MessageJson> = emptyList())

    @Serializable
    private data class MetadataJson(
        val device: JsonObject? = null,
        val filter: String = "",
        val projectApplicationIds: List<String> = emptyList(),
    )

    @Serializable
    private data class MessageJson(val header: HeaderJson, val message: String = "")

    @Serializable
    private data class HeaderJson(
        val logLevel: String,
        val pid: Int = 0,
        val tid: Int = 0,
        val applicationId: String = "",
        val processName: String = "",
        val tag: String = "",
        val timestamp: TimestampJson = TimestampJson(0, 0),
    )

    @Serializable
    private data class TimestampJson(val seconds: Long, val nanos: Int = 0)
}
