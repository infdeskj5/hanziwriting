package com.infdesk.hanziwriting.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DebugLog {
    private var logFile: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.filesDir, "debug_log.txt")
        // Keep the log file from growing forever
        if (logFile!!.exists() && logFile!!.length() > 512 * 1024) {
            logFile!!.writeText("")
        }
    }

    fun getLogFile(): File? = logFile

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("D/$tag: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val sb = StringBuilder("E/$tag: $message")
        throwable?.let { sb.append("\n").append(it.stackTraceToString()) }
        write(sb.toString())
    }

    private fun write(message: String) {
        try {
            logFile?.appendText("${timeFormat.format(Date())} $message\n")
        } catch (_: Exception) { }
    }
}
