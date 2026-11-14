package com.infdesk.hanziwriting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HanziCharacter::class, HanziGraphics::class, PracticeList::class, ListItem::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hanziDao(): HanziDao

    companion object {
        private const val DATABASE_NAME = "hanzi_database"
        private const val PREBUILT_DATABASE_ASSET = "hanzi_database_prebuilt.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * The release workflow creates this database from the CEDICT, MakeMeAHanzi
         * dictionary, and graphics assets. If it is absent (for a local development
         * build), DatabaseInitializer falls back to the original asset parser.
         */
        fun hasPrebuiltDatabase(context: Context): Boolean {
            return context.applicationContext.assets
                .list("")
                ?.contains(PREBUILT_DATABASE_ASSET) == true
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val builder = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )

                    // Room copies this pre-populated SQLite database on first creation.
                    // Existing databases are never overwritten, preserving user lists.
                    if (hasPrebuiltDatabase(appContext)) {
                        builder.createFromAsset(PREBUILT_DATABASE_ASSET)
                    }

                    val instance = builder
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
