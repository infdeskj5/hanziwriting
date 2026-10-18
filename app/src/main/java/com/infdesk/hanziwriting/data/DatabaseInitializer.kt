package com.infdesk.hanziwriting.data

import android.content.Context
import com.infdesk.hanziwriting.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    private const val PREFS_NAME = "hanzi_init"
    private const val KEY_INIT_VERSION = "init_version"

    // Bump this number any time you need to force the app to rebuild its data.
    private const val CURRENT_INIT_VERSION = 1

    suspend fun initializeIfNeeded(context: Context, database: AppDatabase) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedVersion = prefs.getInt(KEY_INIT_VERSION, 0)

            if (storedVersion < CURRENT_INIT_VERSION) {
                DebugLog.d("DBInit", "Re-initializing database ($storedVersion -> $CURRENT_INIT_VERSION)")
                try {
                    val dao = database.hanziDao()
                    // Clear only the reference tables, keep the user's practice lists
                    dao.clearCharacters()
                    dao.clearGraphics()
                    DebugLog.d("DBInit", "Reference tables cleared")

                    AssetParser.parseAndSaveDictionary(context, dao)
                    AssetParser.parseAndSaveGraphics(context, dao)
                    AssetParser.parseAndSaveRadicals(context, dao)

                    prefs.edit().putInt(KEY_INIT_VERSION, CURRENT_INIT_VERSION).apply()
                    DebugLog.d("DBInit", "Initialization complete")
                } catch (e: Exception) {
                    DebugLog.e("DBInit", "Initialization failed", e)
                }
            } else {
                DebugLog.d("DBInit", "Database already initialized (version $storedVersion)")
            }
        }
    }
}
