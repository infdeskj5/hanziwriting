package com.infdesk.hanziwriting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HanziCharacter::class,
        HanziGraphics::class,
        PracticeList::class,
        ListItem::class,
        HanziWordForm::class,
        VocabularyEntry::class,
        StudyListBinding::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hanziDao(): HanziDao

    companion object {
        private const val DATABASE_NAME = "hanzi_database"
        private const val PREBUILT_DATABASE_ASSET = "hanzi_database_prebuilt.db"
        const val VOCABULARY_METADATA_ASSET = "hanzi_vocabulary_metadata.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS hanzi_word_forms (simplified TEXT NOT NULL, traditional TEXT NOT NULL, PRIMARY KEY(simplified, traditional))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_hanzi_word_forms_traditional ON hanzi_word_forms(traditional)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS vocabulary_entries (source TEXT NOT NULL, entryId TEXT NOT NULL, level TEXT NOT NULL, simplified TEXT NOT NULL, traditional TEXT NOT NULL, preferredForm TEXT NOT NULL, pinyin TEXT NOT NULL, partOfSpeech TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(source, entryId))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vocabulary_entries_source_level ON vocabulary_entries(source, level)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vocabulary_entries_simplified ON vocabulary_entries(simplified)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vocabulary_entries_traditional ON vocabulary_entries(traditional)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS study_list_bindings (source TEXT NOT NULL, level TEXT NOT NULL, listId INTEGER NOT NULL, PRIMARY KEY(source, level))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_study_list_bindings_listId ON study_list_bindings(listId)"
                )
            }
        }

        fun hasPrebuiltDatabase(context: Context): Boolean =
            context.applicationContext.assets.list("")?.contains(PREBUILT_DATABASE_ASSET) == true

        fun hasVocabularyMetadata(context: Context): Boolean =
            context.applicationContext.assets.list("")?.contains(VOCABULARY_METADATA_ASSET) == true

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val builder = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )

                    if (hasPrebuiltDatabase(appContext)) {
                        builder.createFromAsset(PREBUILT_DATABASE_ASSET)
                    }

                    val instance = builder
                        .addMigrations(MIGRATION_2_3)
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
