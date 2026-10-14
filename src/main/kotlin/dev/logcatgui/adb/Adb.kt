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

import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs `adb` commands. [path] is null when no adb binary could be found.
 *
 * Long-running processes started with [startStreaming] are tracked and destroyed on
 * [destroyAll] and at JVM shutdown, so closing the app never leaves `adb logcat`
 * clients running in the background.
 */
class Adb(val path: String?) {
    val isAvailable: Boolean get() = path != null

    private val live: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    init {
        Runtime.getRuntime().addShutdownHook(thread(start = false, name = "adb-cleanup") { destroyAll() })
    }

    fun command(vararg args: String): List<String> = listOf(path ?: "adb") + args

    /**
     * Starts the adb server with all output discarded. Do this before any [run]: if the
     * server is started implicitly by a command whose stdout is piped to us, the server
     * process can inherit that pipe and the read never sees EOF.
     */
    fun startServer() {
        try {
            ProcessBuilder(command("start-server"))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor(15, TimeUnit.SECONDS)
        } catch (_: IOException) {
            // Reported later, when a real command fails.
        }
    }

    /** Runs a short command and returns its stdout lines, or an empty list on failure or timeout. */
    fun run(vararg args: String, timeoutMs: Long = 10_000): List<String> {
        val process = try {
            ProcessBuilder(command(*args))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (_: IOException) {
            return emptyList()
        }
        val output = CompletableFuture.supplyAsync {
            try {
                process.inputStream.bufferedReader().readLines()
            } catch (_: IOException) {
                emptyList()
            }
        }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return emptyList()
        }
        return try {
            output.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Runs a command and returns its raw stdout (e.g. a PNG from `exec-out screencap -p`). */
    fun runBytes(vararg args: String, timeoutMs: Long = 30_000): ByteArray {
        val process = ProcessBuilder(command(*args))
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = CompletableFuture.supplyAsync { process.inputStream.readBytes() }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw IOException("adb ${args.joinToString(" ")} timed out")
        }
        return output.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** Starts a long-running command (stderr discarded). The caller reads stdout and destroys it. */
    fun startStreaming(vararg args: String): Process {
        val process = ProcessBuilder(command(*args))
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        live += process
        process.onExit().thenRun { live -= process }
        return process
    }

    fun destroyAll() {
        live.forEach { it.destroy() }
    }

    companion object {
        private val osName = System.getProperty("os.name")
        private val isWindows = osName.startsWith("Windows", ignoreCase = true)
        private val isMac = osName.startsWith("Mac", ignoreCase = true)

        /**
         * Looks for adb in, in order: `ADB_PATH`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `PATH`,
         * the default Android Studio SDK location, then common package-manager locations.
         *
         * The last two matter on macOS: apps started from Finder or the Dock get a minimal
         * `PATH` that doesn't include Homebrew or the SDK.
         */
        fun locate(): Adb {
            val exe = if (isWindows) "adb.exe" else "adb"
            val home = System.getProperty("user.home")
            val candidates = buildList {
                System.getenv("ADB_PATH")?.let { add(File(it)) }
                for (variable in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
                    System.getenv(variable)?.let { add(File(it, "platform-tools/$exe")) }
                }
                System.getenv("PATH")?.split(File.pathSeparator)?.forEach { add(File(it, exe)) }
                when {
                    isWindows -> System.getenv("LOCALAPPDATA")?.let { add(File(it, "Android/Sdk/platform-tools/$exe")) }
                    isMac -> {
                        add(File(home, "Library/Android/sdk/platform-tools/$exe"))
                        add(File("/opt/homebrew/bin/$exe"))            // Homebrew, Apple silicon
                        add(File("/usr/local/bin/$exe"))               // Homebrew, Intel
                    }
                    else -> {
                        add(File(home, "Android/Sdk/platform-tools/$exe"))
                        add(File("/usr/bin/$exe"))                     // distro package (android-tools)
                        add(File("/usr/lib/android-sdk/platform-tools/$exe"))
                        add(File("/opt/android-sdk/platform-tools/$exe"))
                    }
                }
            }
            return Adb(candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath)
        }
    }
}
