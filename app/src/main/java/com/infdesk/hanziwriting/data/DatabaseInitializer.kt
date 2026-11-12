package com.infdesk.hanziwriting.data

import android.content.Context
import com.infdesk.hanziwriting.utils.AppState
import com.infdesk.hanziwriting.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    private const val PREFS_NAME = "hanzi_init"
    private const val KEY_INIT_VERSION = "init_version"
    private const val CURRENT_INIT_VERSION = 4

    /** Safe, quick preference check used before the first Compose frame. */
    fun needsInitialization(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_INIT_VERSION, 0) < CURRENT_INIT_VERSION
    }

    suspend fun initializeIfNeeded(context: Context, database: AppDatabase): Boolean {
        if (!needsInitialization(context)) {
            AppState.isInitializing = false
            return true
        }

        AppState.isInitializing = true
        AppState.initProgress = 0f
        AppState.initMessage = "Preparing Hanzi Writing…"

        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            try {
                val dao = database.hanziDao()

                AppState.initMessage = "Clearing old dictionary data…"
                AppState.initProgress = 0.05f
                dao.clearCharacters()
                dao.clearGraphics()

                AppState.initMessage = "Loading dictionary…"
                AppState.initProgress = 0.15f
                AssetParser.parseAndSaveDictionary(context, dao)

                AppState.initMessage = "Loading stroke graphics…"
                AppState.initProgress = 0.45f
                AssetParser.parseAndSaveGraphics(context, dao)

                AppState.initMessage = "Loading radicals & components…"
                AppState.initProgress = 0.80f
                AssetParser.parseAndSaveRadicals(context, dao)

                prefs.edit().putInt(KEY_INIT_VERSION, CURRENT_INIT_VERSION).apply()
                AppState.initProgress = 1f
                AppState.initMessage = "Ready!"
                DebugLog.d("DBInit", "Initialization complete")
                true
            } catch (error: Exception) {
                AppState.initMessage = "Initialization failed. Check storage and try again."
                DebugLog.e("DBInit", "Initialization failed", error)
                false
            } finally {
                AppState.isInitializing = false
            }
        }
    }
}
