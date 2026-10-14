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
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogcatFilesTest {

    private val message = LogcatMessage(
        LogcatHeader(LogLevel.WARN, 21903, 16767, "com.example.app", "com.example.app:sync", "Bundle", Instant.ofEpochSecond(1760398573, 935_000_000)),
        "Key name expected\n\tat android.os.BaseBundle.getValue(BaseBundle.java:398)",
    )

    @Test
    fun exportImportRoundTrip() {
        val dir = createTempDirectory("logcat-gui-test").toFile()
        try {
            val file = File(dir, "test.logcat")
            val device = Device("adb-R52Y306CTHD", "device", manufacturer = "samsung", model = "SM-X710", release = "16", apiLevel = "36.1")
            LogcatFiles.export(file, listOf(message), device, "tag:Bundle")
            val data = LogcatFiles.import(file)
            assertEquals(listOf(message), data.messages)
            assertEquals("tag:Bundle", data.filter)
            assertEquals("samsung SM-X710 Android 16, API 36.1", data.deviceDescription)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun exportUsesAndroidStudiosLayout() {
        val dir = createTempDirectory("logcat-gui-test").toFile()
        try {
            val file = File(dir, "test.logcat")
            LogcatFiles.export(file, listOf(message), Device("emulator-5554", "device", manufacturer = "Google", model = "sdk_gphone64", release = "14", apiLevel = "34"), "")
            val json = file.readText()
            for (expected in listOf(
                "\"metadata\"", "\"emulatorDevice\"", "\"apiLevel\": {", "\"majorVersion\": 34", "\"logcatMessages\"",
                "\"logLevel\": \"WARN\"", "\"timestamp\": {", "\"seconds\": 1760398573", "\"nanos\": 935000000",
            )) {
                assertTrue(expected in json, "missing $expected in\n$json")
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun importsAnAndroidStudioExport() {
        // Shape written by Android Studio's Gson-based LogcatFileIo.
        val json = """
            {
              "metadata": {
                "device": {
                  "physicalDevice": {
                    "deviceId": { "pluginId": "PhysicalDevice", "isTemplate": false, "identifier": "serial=QV702XTV0X" },
                    "serialNumber": "QV702XTV0X",
                    "isOnline": true,
                    "release": "9",
                    "apiLevel": { "majorVersion": 28, "minorVersion": 0 },
                    "featureLevel": 28,
                    "manufacturer": "Sony",
                    "model": "G8341",
                    "type": "HANDHELD"
                  }
                },
                "filter": "package:mine",
                "projectApplicationIds": [ "com.example.app" ]
              },
              "logcatMessages": [
                {
                  "header": {
                    "logLevel": "ERROR",
                    "pid": 1267,
                    "tid": 1399,
                    "applicationId": "storaged",
                    "processName": "storaged",
                    "tag": "storaged",
                    "timestamp": { "seconds": 1760398579, "nanos": 223000000 }
                  },
                  "message": "getDiskStats failed with result NOT_SUPPORTED and size 0"
                }
              ]
            }
        """.trimIndent()
        val data = LogcatFiles.importJson(json)
        val m = data.messages.single()
        assertEquals(LogLevel.ERROR, m.header.logLevel)
        assertEquals("storaged", m.header.tag)
        assertEquals(Instant.ofEpochSecond(1760398579, 223_000_000), m.header.timestamp)
        assertEquals("Sony G8341 Android 9, API 28", data.deviceDescription)
        assertEquals("package:mine", data.filter)
    }

    @Test
    fun parsesThreadtimeAndMergesContinuationLines() {
        val lines = sequenceOf(
            "--------- beginning of main",
            "10-14 00:26:13.935 21903 16767 W Bundle  : Key name expected",
            "10-14 00:26:13.935 21903 16767 W Bundle  : \tat android.os.BaseBundle.getValue(BaseBundle.java:398)",
            "10-14 00:26:19.223  1267  1399 E storaged: getDiskStats failed",
        )
        val messages = LogcatFiles.parseText(lines, year = 2026, zone = ZoneOffset.UTC)
        assertEquals(2, messages.size)
        assertEquals("Bundle", messages[0].header.tag)
        assertEquals(21903, messages[0].header.pid)
        assertEquals("Key name expected\n\tat android.os.BaseBundle.getValue(BaseBundle.java:398)", messages[0].message)
        assertEquals(LocalDateTime.of(2026, 10, 14, 0, 26, 13, 935_000_000).toInstant(ZoneOffset.UTC), messages[0].header.timestamp)
        assertEquals(LogLevel.ERROR, messages[1].header.logLevel)
        assertEquals("storaged", messages[1].header.tag)
    }

    @Test
    fun parsesUidAndFirebaseFormats() {
        val messages = LogcatFiles.parseText(
            sequenceOf(
                "10-14 00:26:13.935  1000  1517  1859 I ActivityManager: Start proc",
                "10-14 00:26:14.001: D/OkHttp( 4321): --> GET https://example.com",
            ),
            year = 2026,
            zone = ZoneOffset.UTC,
        )
        assertEquals(listOf(1517 to "ActivityManager", 4321 to "OkHttp"), messages.map { it.header.pid to it.header.tag })
        assertEquals(listOf("Start proc", "--> GET https://example.com"), messages.map { it.message })
    }
}
