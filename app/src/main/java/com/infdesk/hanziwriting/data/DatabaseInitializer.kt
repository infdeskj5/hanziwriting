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

    suspend fun initializeIfNeeded(context: Context, database: AppDatabase) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt(KEY_INIT_VERSION, 0)

        if (storedVersion >= CURRENT_INIT_VERSION) {
            AppState.isInitializing = false
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val dao = database.hanziDao()

                AppState.initMessage = "Clearing old data…"
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
            } catch (e: Exception) {
                DebugLog.e("DBInit", "Initialization failed", e)
            } finally {
                AppState.isInitializing = false
            }
        }
    }
}
