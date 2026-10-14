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
package dev.logcatgui.adb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdbOutputParsingTest {

    @Test
    fun parsesDevicesList() {
        val devices = DeviceService.parseDevices(
            listOf(
                "* daemon not running; starting now at tcp:5037",
                "* daemon started successfully",
                "List of devices attached",
                "emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1",
                "R58M123ABC             unauthorized usb:1-1 transport_id:2",
                "adb-3C161FDJG001EK-DJzriv._adb-tls-connect._tcp device product:husky model:Pixel_8_Pro device:husky transport_id:93",
                "",
            )
        )
        assertEquals(listOf("emulator-5554", "R58M123ABC", "adb-3C161FDJG001EK-DJzriv._adb-tls-connect._tcp"), devices.map { it.serial })
        assertTrue(devices[0].isOnline)
        assertTrue(devices[0].isEmulator)
        assertFalse(devices[1].isOnline)
        assertEquals("Pixel 8 Pro", devices[2].name, "falls back to the adb model until props load")
    }

    @Test
    fun parsesProps() {
        val props = DeviceService.parseProps(listOf("samsung", "SM-X710", "16", "36.1", "36", "0", ""))!!
        assertEquals("samsung", props.manufacturer)
        assertEquals("SM-X710", props.model)
        assertEquals("16", props.release)
        assertEquals("36.1", props.apiLevel)
        assertFalse(props.isQemu)
    }

    @Test
    fun propsFallBackToPlainSdkAndDetectEmulators() {
        val props = DeviceService.parseProps(listOf("Google", "sdk_gphone64", "14", "", "34", "", "1"))!!
        assertEquals("34", props.apiLevel)
        assertTrue(props.isQemu)
        assertNull(DeviceService.parseProps(listOf("error: closed")))
    }

    @Test
    fun labelsMatchAndroidStudio() {
        val online = Device("adb-R52Y306CTHD-3jbvWR._adb-tls-connect._tcp", "device", manufacturer = "samsung", model = "SM-X710", release = "16", apiLevel = "36.1")
        assertEquals("samsung SM-X710 (adb-R52Y306CTHD-3jbvWR._adb-tls-connect._tcp)", online.label)
        assertEquals("Android 16, API 36.1", online.details)
        assertNull(online.statusTag)

        val gone = online.copy(serial = "emulator-5554", state = Device.DISCONNECTED, manufacturer = "OPPO", model = "CPH1823", release = "12", apiLevel = "32")
        assertEquals("OPPO CPH1823", gone.label, "offline devices drop the serial, as in AS")
        assertEquals("[OFFLINE]", gone.statusTag)
        assertEquals("[UNAUTHORIZED]", Device("X", "unauthorized").statusTag)
        assertEquals(36, online.sdkMajor)
    }

    @Test
    fun parsesPidNamePs() {
        val names = ProcessNameResolver.parsePs(
            listOf("  PID NAME", "    1 init", " 4321 com.example.app", " 4400 com.example.app:remote")
        )
        assertEquals(mapOf(1 to "init", 4321 to "com.example.app", 4400 to "com.example.app:remote"), names)
    }

    @Test
    fun parsesLegacyFullPs() {
        val names = ProcessNameResolver.parsePs(
            listOf(
                "USER      PID   PPID  VSIZE  RSS   WCHAN              PC  NAME",
                "root      1     0     8904   788   SyS_epoll_ 0000000000 S /init",
                "u0_a123   4321  300   1234   567   SyS_epoll_ 0000000000 S com.example.app",
            )
        )
        assertEquals(mapOf(1 to "/init", 4321 to "com.example.app"), names)
    }
}
