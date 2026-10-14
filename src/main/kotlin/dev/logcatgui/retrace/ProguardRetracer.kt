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
package dev.logcatgui.retrace

import com.android.tools.r8.retrace.ProguardMapProducer
import com.android.tools.r8.retrace.ProguardMappingSupplier
import com.android.tools.r8.retrace.Retrace
import com.android.tools.r8.retrace.RetraceCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * De-obfuscates stack traces with an R8/ProGuard `mapping.txt`, using R8's retrace API the
 * same way Android Studio's "Load Proguard Mappings…" does.
 *
 * Not thread-safe: R8 reuses one command builder, so call [retrace] from one thread.
 */
class ProguardRetracer private constructor(val path: Path, private val builder: RetraceCommand.Builder) {

    val fileName: String get() = path.fileName.toString()

    /** Returns [message] with obfuscated frames replaced, or unchanged if nothing applies. */
    fun retrace(message: String): String {
        if (!looksLikeStackTrace(message)) return message
        return try {
            val out = ArrayList<String>()
            Retrace.run(builder.setStackTrace(message.split('\n')).setRetracedStackTraceConsumer { out.addAll(it) }.build())
            if (out.isEmpty()) message else out.joinToString("\n")
        } catch (e: Exception) {
            message
        }
    }

    companion object {
        /** Loads the mapping and warms R8 up off the UI thread, so the first frame doesn't stall. */
        suspend fun load(path: Path): ProguardRetracer = withContext(Dispatchers.IO) {
            val builder = RetraceCommand.builder().setMappingSupplier(
                ProguardMappingSupplier.builder().setProguardMapProducer(ProguardMapProducer.fromPath(path)).build()
            )
            ProguardRetracer(path, builder).also { it.retrace("java.lang.Exception\n\tat a.a.a(SourceFile:1)") }
        }

        internal fun looksLikeStackTrace(message: String) = "at " in message
    }
}
