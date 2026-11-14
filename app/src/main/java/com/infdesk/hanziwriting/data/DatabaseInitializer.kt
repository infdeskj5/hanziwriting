package com.infdesk.hanziwriting.data

import android.content.Context
import com.infdesk.hanziwriting.utils.AppState
import com.infdesk.hanziwriting.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DatabaseInitializer {
    private const val PREFS_NAME = "hanzi_init"
    private const val KEY_INIT_VERSION = "init_version"
    // Version 6 adds prebuilt traditional/simplified pairs and HSK/TOCFL metadata.
    private const val CURRENT_INIT_VERSION = 6

    fun needsInitialization(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_INIT_VERSION, 0) < CURRENT_INIT_VERSION

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
                val sqlite = database.openHelper.writableDatabase

                if (AppDatabase.hasPrebuiltDatabase(context)) {
                    // Room copies the prebuilt database on first install. Existing
                    // databases are migrated in place and retain the user's lists.
                    AppState.initMessage = "Opening preloaded Hanzi database…"
                    AppState.initProgress = 0.30f
                    val characterCount = scalarCount(sqlite, "SELECT COUNT(*) FROM hanzi_characters")
                    val graphicsCount = scalarCount(sqlite, "SELECT COUNT(*) FROM hanzi_graphics")
                    check(characterCount > 0L && graphicsCount > 0L) {
                        "The prebuilt Hanzi database is empty or incomplete."
                    }
                    DebugLog.d("DBInit", "Prebuilt DB opened: $characterCount characters, $graphicsCount graphics")
                } else {
                    // Development fallback for local builds that do not run the CI
                    // prebuild workflow. Release APKs use the prebuilt database.
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
                    check(scalarCount(sqlite, "SELECT COUNT(*) FROM hanzi_characters") > 0L) {
                        "The Hanzi dictionary is empty after initialization."
                    }
                }

                val embeddedForms = scalarCount(sqlite, "SELECT COUNT(*) FROM hanzi_word_forms")
                val embeddedEntries = scalarCount(sqlite, "SELECT COUNT(*) FROM vocabulary_entries")
                if (embeddedForms > 0L && embeddedEntries > 0L) {
                    // New installs already contain all curriculum metadata in the
                    // main prebuilt Room DB, so there is nothing to import at startup.
                    DebugLog.d("DBInit", "Prebuilt script and curriculum metadata already present")
                } else if (AppDatabase.hasVocabularyMetadata(context)) {
                    // This path is only needed for existing installations whose Room
                    // database predates the new vocabulary tables/data.
                    AppState.initMessage = "Updating vocabulary data…"
                    AppState.initProgress = 0.82f
                    importVocabularyMetadata(context, sqlite)
                } else {
                    DebugLog.w("DBInit", "Vocabulary metadata is missing; rebuild the app with the curriculum datasets")
                }

                prefs.edit().putInt(KEY_INIT_VERSION, CURRENT_INIT_VERSION).apply()
                AppState.initProgress = 1f
                AppState.initMessage = "Ready!"
                DebugLog.d("DBInit", "Initialization complete")
                true
            } catch (error: Exception) {
                AppState.initMessage = "Initialization failed. Check the database assets and available storage."
                DebugLog.e("DBInit", "Initialization failed", error)
                false
            } finally {
                AppState.isInitializing = false
            }
        }
    }

    private fun scalarCount(db: androidx.sqlite.db.SupportSQLiteDatabase, query: String): Long =
        db.query(query).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }

    /**
     * Upgrade-only path: attach the already-indexed metadata DB and copy it with SQL.
     * New installs already have the metadata in their main prebuilt Room DB. No
     * JSON/CSV parsing or thousands of Room insert calls occur on-device.
     */
    private fun importVocabularyMetadata(
        context: Context,
        destination: androidx.sqlite.db.SupportSQLiteDatabase
    ) {
        val existingForms = scalarCount(destination, "SELECT COUNT(*) FROM hanzi_word_forms")
        val existingEntries = scalarCount(destination, "SELECT COUNT(*) FROM vocabulary_entries")
        if (existingForms > 0L && existingEntries > 0L) {
            DebugLog.d("DBInit", "Vocabulary metadata already present; skipping import")
            return
        }

        val cachedFile = File(context.cacheDir, "hanzi_vocabulary_metadata_import.db")
        context.assets.open(AppDatabase.VOCABULARY_METADATA_ASSET).use { input ->
            FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
        }

        var attached = false
        try {
            destination.execSQL("ATTACH DATABASE ? AS vocabulary_source", arrayOf(cachedFile.absolutePath))
            attached = true
            destination.beginTransaction()
            try {
                destination.execSQL(
                    "INSERT OR IGNORE INTO hanzi_word_forms (simplified, traditional) " +
                        "SELECT simplified, traditional FROM vocabulary_source.hanzi_word_forms"
                )
                destination.execSQL(
                    "INSERT OR IGNORE INTO vocabulary_entries " +
                        "(source, entryId, level, simplified, traditional, preferredForm, pinyin, partOfSpeech, sortOrder) " +
                        "SELECT source, entryId, level, simplified, traditional, preferredForm, pinyin, partOfSpeech, sortOrder " +
                        "FROM vocabulary_source.vocabulary_entries"
                )
                destination.setTransactionSuccessful()
            } finally {
                destination.endTransaction()
            }
        } finally {
            if (attached) runCatching { destination.execSQL("DETACH DATABASE vocabulary_source") }
            cachedFile.delete()
        }

        val forms = scalarCount(destination, "SELECT COUNT(*) FROM hanzi_word_forms")
        val entries = scalarCount(destination, "SELECT COUNT(*) FROM vocabulary_entries")
        check(forms > 0L && entries > 0L) { "The prebuilt vocabulary metadata is empty or incomplete." }
        DebugLog.d("DBInit", "Vocabulary metadata imported: $forms script pairs, $entries level entries")
    }
}
