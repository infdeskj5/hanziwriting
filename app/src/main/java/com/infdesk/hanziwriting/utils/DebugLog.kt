package com.infdesk.hanziwriting.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DebugLog {
    private var logFile: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "debug-log").apply { isDaemon = true }
    }

    fun init(context: Context) {
        executor.execute {
            val f = File(context.filesDir, "debug_log.txt")
            if (f.exists() && f.length() > 512 * 1024) f.writeText("")
            logFile = f
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

    /** Blocks until all queued lines are on disk. Call before reading the log. */
    fun flushSync(timeoutMs: Long = 3000L) {
        runCatching { executor.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    private fun write(message: String) {
        val now = Date()
        executor.execute {
            runCatching {
                logFile?.appendText("${timeFormat.format(now)} $message\n")
            }
        }
    }
}
