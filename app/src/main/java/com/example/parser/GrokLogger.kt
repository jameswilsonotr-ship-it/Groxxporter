package com.example.parser

import android.content.Context
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

    private var logFile: File? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun initialize(context: Context, customLogFile: File? = null) {
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
            if (updated.size > 200) {
                updated.removeAt(0) // Keep the last 200 logs to prevent memory leak
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
}
