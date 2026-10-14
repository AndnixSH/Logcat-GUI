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
package dev.logcatgui.model

import java.time.Instant

/**
 * Metadata for one logcat entry. Mirrors the shape of Android Studio's `LogcatHeader`.
 *
 * [processName] and [applicationId] are not part of the logcat stream; they are filled in
 * from the device's process table and are empty when the pid could not be resolved.
 */
data class LogcatHeader(
    val logLevel: LogLevel,
    val pid: Int,
    val tid: Int,
    val applicationId: String,
    val processName: String,
    val tag: String,
    val timestamp: Instant,
)

/** One logcat entry. [message] may span several lines (for example a stack trace). */
data class LogcatMessage(
    val header: LogcatHeader,
    val message: String,
)
