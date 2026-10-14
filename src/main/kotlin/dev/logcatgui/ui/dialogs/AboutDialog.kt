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
package dev.logcatgui.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.logcatgui.AppInfo
import dev.logcatgui.LocalAppServices
import dev.logcatgui.platform.Clipboard
import dev.logcatgui.platform.Shell
import dev.logcatgui.settings.SettingsStore
import dev.logcatgui.ui.components.HorizontalLine
import dev.logcatgui.ui.components.IdeButton
import dev.logcatgui.ui.components.IdeLink
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.Icon
import dev.logcatgui.ui.theme.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutDialog(onClose: () -> Unit) {
    IdeDialog("About ${AppInfo.NAME}", onCloseRequest = onClose, size = DpSize(620.dp, 600.dp), resizable = false) {
        AboutContent(onClose)
    }
}

@Composable
internal fun AboutContent(onClose: () -> Unit) {
    val services = LocalAppServices.current
    var adbVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        adbVersion = withContext(Dispatchers.IO) {
            if (services.adb.isAvailable) services.adb.run("version").firstOrNull()?.trim() else null
        } ?: "not found"
    }
    val runtime = "Java ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
    val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}"
    val settingsFile = SettingsStore.defaultFile()

    val info = buildString {
        appendLine("${AppInfo.NAME} ${AppInfo.version}")
        appendLine("Built with Kotlin ${AppInfo.kotlinVersion} and Compose Multiplatform ${AppInfo.composeVersion}")
        appendLine("Runtime: $runtime")
        appendLine("OS: $os")
        appendLine("adb: ${services.adb.path ?: "not found"} (${adbVersion ?: "…"})")
        append("Settings: ${settingsFile.absolutePath}")
    }

    Column(Modifier.fillMaxSize().background(IdeColors.DialogBg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AppIcon, size = 64.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(AppInfo.NAME, style = IdeText.Bold.copy(fontSize = 22.sp))
                    Text("Version ${AppInfo.version}", style = IdeText.Secondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "A standalone Logcat viewer with the look and workflow of Android Studio's Logcat tool window.",
                style = IdeText.Default,
            )
            Spacer(Modifier.height(16.dp))
            InfoRow("Built with", "Kotlin ${AppInfo.kotlinVersion} · Compose Multiplatform ${AppInfo.composeVersion}")
            InfoRow("Runtime", runtime)
            InfoRow("OS", os)
            InfoRow("adb", listOfNotNull(services.adb.path, adbVersion).joinToString("\n").ifEmpty { "not found" })
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("Settings", Modifier.width(96.dp), style = IdeText.Secondary)
                IdeLink(settingsFile.absolutePath, onClick = {
                    if (settingsFile.isFile) Shell.reveal(settingsFile) else Shell.open(settingsFile.parentFile ?: settingsFile)
                })
            }
            Spacer(Modifier.height(16.dp))
            Text("Copyright 2026 The Logcat-GUI Authors. Licensed under the Apache License, Version 2.0.", style = IdeText.Default)
            Spacer(Modifier.height(12.dp))
            Text("Acknowledgements", style = IdeText.Bold)
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Credit("Design, Logcat color scheme and tag palette modeled on Android Studio's Logcat", "Android Open Source Project · Apache 2.0")
                Credit("Icons from the IntelliJ Platform", "JetBrains s.r.o. · Apache 2.0")
                Credit("Device, screenshot and screen-record icons from Android Studio", "Android Open Source Project · Apache 2.0")
                Credit("Colors from the IntelliJ \"Islands Dark\" theme", "JetBrains s.r.o. · Apache 2.0")
                Credit("Inter and JetBrains Mono fonts", "SIL Open Font License 1.1")
                Credit("R8 retrace (ProGuard mapping support)", "The R8 project authors · BSD 3-Clause")
                Credit("Kotlin, kotlinx.coroutines, kotlinx.serialization, Compose Multiplatform", "JetBrains s.r.o. · Apache 2.0")
                Credit("Java Native Access (JNA)", "Apache 2.0 / LGPL 2.1")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "\"Android\" and \"Android Studio\" are trademarks of Google LLC. This product is not affiliated with Google LLC or JetBrains s.r.o.",
                style = IdeText.Small.copy(color = IdeColors.TextSecondary),
            )
        }
        HorizontalLine(color = IdeColors.PanelBorder)
        DialogButtons {
            IdeButton("Copy Info", { Clipboard.copyText(info) })
            IdeButton("Close", onClose, default = true)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, Modifier.width(96.dp), style = IdeText.Secondary)
        Text(value, style = IdeText.Default)
    }
}

@Composable
private fun Credit(what: String, who: String) {
    Row {
        Text("•  ", style = IdeText.Secondary)
        Column {
            Text(what, style = IdeText.Default)
            Text(who, style = IdeText.Small.copy(color = IdeColors.TextSecondary))
        }
    }
}
