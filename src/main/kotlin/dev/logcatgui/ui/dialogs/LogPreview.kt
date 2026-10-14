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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.logcatgui.model.LogLevel
import dev.logcatgui.model.LogcatHeader
import dev.logcatgui.model.LogcatMessage
import dev.logcatgui.settings.FormattingOptions
import dev.logcatgui.ui.panel.MessageFormatter
import dev.logcatgui.ui.theme.IdeColors
import dev.logcatgui.ui.theme.IdeText
import dev.logcatgui.ui.theme.LogColors
import java.time.Instant

/** Sample lines rendered with the real formatter, for the format and color pages. */
@Composable
internal fun LogPreview(options: FormattingOptions, colors: LogColors) {
    val samples = remember { sampleMessages() }
    val shape = RoundedCornerShape(6.dp)
    // Whole lines wrap (as AS does in a narrow editor) so every column stays in view.
    Column(
        Modifier
            .fillMaxWidth()
            .background(IdeColors.EditorBg, shape)
            .border(1.dp, IdeColors.PanelBorder, shape)
            .padding(10.dp),
    ) {
        samples.forEachIndexed { i, m ->
            val line = MessageFormatter.header(m, samples.getOrNull(i - 1), options, colors) +
                AnnotatedString(" ") + MessageFormatter.body(m, null, colors)
            Text(line, style = IdeText.Mono.copy(fontSize = 12.sp))
        }
    }
}

private fun sampleMessages(): List<LogcatMessage> {
    val now = Instant.now()
    fun m(level: LogLevel, pid: Int, tid: Int, app: String, tag: String, text: String, ms: Long) =
        LogcatMessage(LogcatHeader(level, pid, tid, app.substringBefore(':'), app, tag, now.plusMillis(ms)), text)
    return listOf(
        m(LogLevel.VERBOSE, 4321, 4321, "com.example.app", "MainActivity", "onCreate()", 0),
        m(LogLevel.DEBUG, 4321, 4388, "com.example.app", "OkHttp", "--> GET https://example.com/api/items", 12),
        m(LogLevel.DEBUG, 4321, 4388, "com.example.app", "OkHttp", "<-- 200 OK (84ms)", 96),
        m(LogLevel.INFO, 1517, 1859, "system_server", "ActivityManager", "Start proc 4321:com.example.app/u0a123", 140),
        m(LogLevel.WARN, 4321, 4321, "com.example.app:sync", "SyncWorker", "Retrying in 30s", 210),
        m(LogLevel.ERROR, 4321, 4321, "com.example.app", "AndroidRuntime", "FATAL EXCEPTION: main", 260),
        m(LogLevel.ASSERT, 4321, 4400, "com.example.app", "libc", "Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE)", 300),
    )
}
