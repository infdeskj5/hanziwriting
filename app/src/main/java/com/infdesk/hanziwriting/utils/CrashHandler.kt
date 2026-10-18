package com.infdesk.hanziwriting.utils

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashHandler : Thread.UncaughtExceptionHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val file = File(context.filesDir, "crash_log_$timestamp.txt")
        
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        
        file.writeText("Crash at: $timestamp\nThread: ${t.name}\n\n${sw.toString()}\n")
        defaultHandler?.uncaughtException(t, e)
    }

    fun getCrashLogs(): List<File> {
        return context.filesDir.listFiles { file -> file.name.startsWith("crash_log_") }?.toList() ?: emptyList()
    }

    fun clearCrashLogs() {
        getCrashLogs().forEach { it.delete() }
    }
}
