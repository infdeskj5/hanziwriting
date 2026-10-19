package com.infdesk.hanziwriting.utils

import android.content.Context
import android.content.SharedPreferences

object ProgressStore {
    private const val PREFS_NAME = "practice_progress"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSuccessCount(character: String): Int =
        if (::prefs.isInitialized) prefs.getInt(character, 0) else 0

    fun incrementSuccess(character: String) {
        if (::prefs.isInitialized) {
            prefs.edit().putInt(character, getSuccessCount(character) + 1).apply()
        }
    }
}
