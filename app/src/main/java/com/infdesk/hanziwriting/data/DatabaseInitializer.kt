package com.infdesk.hanziwriting.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    suspend fun initializeIfNeeded(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            // Quick check: if searching for "a" returns nothing, the DB is likely empty
            val isEmpty = dao.searchHanzi("a").isEmpty()
            if (isEmpty) {
                // Parse and save in the background
                AssetParser.parseAndSaveDictionary(context, dao)
                AssetParser.parseAndSaveGraphics(context, dao)
            }
        }
    }
}
