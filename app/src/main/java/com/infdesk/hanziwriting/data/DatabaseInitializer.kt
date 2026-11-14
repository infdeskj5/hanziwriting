package com.infdesk.hanziwriting.data

import android.content.Context
import com.infdesk.hanziwriting.utils.AppState
import com.infdesk.hanziwriting.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    private const val PREFS_NAME = "hanzi_init"
    private const val KEY_INIT_VERSION = "init_version"

    // Version 5 switches release installs to the prebuilt Room database.
    // Existing databases are opened in place; user-created lists are not cleared.
    private const val CURRENT_INIT_VERSION = 5

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
                if (AppDatabase.hasPrebuiltDatabase(context)) {
                    // Opening the Room helper triggers Room's one-time copy of the
                    // prebuilt database from assets. This replaces thousands of
                    // per-row JSON parsing and insert operations on the phone.
                    AppState.initMessage = "Opening preloaded Hanzi database…"
                    AppState.initProgress = 0.35f

                    val sqlite = database.openHelper.writableDatabase
                    val characterCount = sqlite.query(
                        "SELECT COUNT(*) FROM hanzi_characters"
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                    }
                    val graphicsCount = sqlite.query(
                        "SELECT COUNT(*) FROM hanzi_graphics"
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                    }

                    check(characterCount > 0L && graphicsCount > 0L) {
                        "The prebuilt Hanzi database is empty or incomplete."
                    }

                    DebugLog.d(
                        "DBInit",
                        "Prebuilt database opened: $characterCount characters, $graphicsCount graphics"
                    )
                } else {
                    // Compatibility fallback for local Android Studio builds that do
                    // not run the GitHub Actions prebuild step.
                    AppState.initMessage = "Loading dictionary…"
                    AppState.initProgress = 0.10f
                    val dao = database.hanziDao()

                    dao.clearCharacters()
                    dao.clearGraphics()

                    AssetParser.parseAndSaveDictionary(context, dao)
                    AppState.initProgress = 0.40f

                    AppState.initMessage = "Loading stroke graphics…"
                    AssetParser.parseAndSaveGraphics(context, dao)
                    AppState.initProgress = 0.70f

                    AppState.initMessage = "Loading radicals & components…"
                    AssetParser.parseAndSaveRadicals(context, dao)

                    val sqlite = database.openHelper.readableDatabase
                    val characterCount = sqlite.query(
                        "SELECT COUNT(*) FROM hanzi_characters"
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                    }
                    check(characterCount > 0L) { "The Hanzi dictionary is empty after initialization." }
                }

                prefs.edit().putInt(KEY_INIT_VERSION, CURRENT_INIT_VERSION).apply()
                AppState.initProgress = 1f
                AppState.initMessage = "Ready!"
                DebugLog.d("DBInit", "Initialization complete")
                true
            } catch (error: Exception) {
                AppState.initMessage =
                    "Initialization failed. Check the database assets and available storage."
                DebugLog.e("DBInit", "Initialization failed", error)
                false
            } finally {
                AppState.isInitializing = false
            }
        }
    }
}
