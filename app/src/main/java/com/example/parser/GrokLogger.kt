package com.example.parser

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object GrokLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    val isVerboseDebugEnabled = MutableStateFlow(false)

    private var logFile: File? = null
    private var appContext: Context? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun initialize(context: Context, customLogFile: File? = null) {
        appContext = context.applicationContext
        if (customLogFile != null) {
            logFile = customLogFile
        } else {
            val cacheDir = context.cacheDir
            logFile = File(cacheDir, "grok_extraction_log.txt")
        }
        if (logFile?.exists() == true) {
            logFile?.delete()
        }
        _logs.value = emptyList()
        info("Logger initialized. Logging to ${logFile?.absolutePath}")
    }

    private fun writeToFile(level: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val logLine = "[$timestamp] [$level] $message\n"
        
        // Append to in-memory flow
        _logs.update { current ->
            val updated = current.toMutableList()
            updated.add("[$level] $message")
            if (updated.size > 300) {
                updated.removeAt(0) // Keep the last 300 logs
            }
            updated
        }

        try {
            logFile?.let { file ->
                FileOutputStream(file, true).use { out ->
                    out.write(logLine.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun info(message: String) {
        writeToFile("INFO", message)
    }

    fun warn(message: String) {
        writeToFile("WARN", message)
    }

    fun debug(message: String) {
        if (isVerboseDebugEnabled.value) {
            writeToFile("DEBUG", message)
        }
    }

    fun forensics(contextLabel: String, unmappedKey: String? = null, exception: Throwable? = null) {
        if (!isVerboseDebugEnabled.value) return
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
        val sb = StringBuilder()
        sb.append("[$contextLabel] Heap: ${usedMemMb}MB / ${maxMemMb}MB | OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | Model: ${Build.MANUFACTURER} ${Build.MODEL}")
        if (unmappedKey != null) {
            sb.append(" | Unmapped JSON Key Encountered: '$unmappedKey'")
        }
        if (exception != null) {
            sb.append("\nException Trace: ${exception.stackTraceToString()}")
        }
        writeToFile("FORENSICS", sb.toString())
    }

    fun error(message: String, throwable: Throwable? = null) {
        val errMessage = if (throwable != null) {
            "$message: ${throwable.localizedMessage}\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        writeToFile("ERROR", errMessage)
    }

    fun getLogFileContent(): String {
        return try {
            if (logFile?.exists() == true) {
                logFile?.readText(Charsets.UTF_8) ?: ""
            } else {
                "No logs available."
            }
        } catch (e: Exception) {
            "Error reading log file: ${e.localizedMessage}"
        }
    }

    fun exportDiagnosticDump(context: Context): Pair<File, String> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dumpFile = File(context.cacheDir, "grok_debug_dump_$timeStamp.txt")
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val storagePerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        val header = """
            ===================================================================
            PROJECT IRON PEARL - DEEP DIAGNOSTIC & FORENSIC DUMP
            Generated: ${Date()}
            ===================================================================
            [SYSTEM TELEMETRY]
            Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
            Android OS: Version ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Heap Memory Allocation: ${usedMemMb}MB used / ${maxMemMb}MB max
            Storage Access Permission Granted: $storagePerm
            Verbose Forensics Mode Active: ${isVerboseDebugEnabled.value}
            
            [APPLICATION LOG HISTORY]
            ${getLogFileContent()}
            ===================================================================
            END OF DIAGNOSTIC DUMP
            ===================================================================
        """.trimIndent()

        dumpFile.writeText(header, Charsets.UTF_8)
        return Pair(dumpFile, header)
    }
}

