package com.infdesk.hanziwriting.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    suspend fun initializeIfNeeded(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            val isEmpty = dao.searchHanzi("a").isEmpty()
            if (isEmpty) {
                AssetParser.parseAndSaveDictionary(context, dao)
                AssetParser.parseAndSaveGraphics(context, dao)
                AssetParser.parseAndSaveRadicals(context, dao)
            }
        }
    }
}
