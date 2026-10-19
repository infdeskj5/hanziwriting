package com.infdesk.hanziwriting.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.infdesk.hanziwriting.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AssetParser {
    suspend fun parseAndSaveDictionary(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            val reader = context.assets.open("cedict_ts.u8").bufferedReader()
            val charactersToInsert = mutableListOf<HanziCharacter>()
            val regex = Regex("^(.+?)\\s+(.+?)\\s+\\[(.+?)\\]\\s+/(.+)/$")

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#") || line.isBlank()) return@forEach
                    val match = regex.find(line)
                    if (match != null) {
                        val (_, simplified, pinyin, definition) = match.destructured
                        charactersToInsert.add(
                            HanziCharacter(
                                character = simplified,
                                pinyin = pinyin,
                                definition = definition.replace("/", ", "),
                                radical = "",
                                decomposition = "" // Will be updated by radicals parser
                            )
                        )
                    }
                }
            }
            charactersToInsert.chunked(1000).forEach { dao.insertCharacters(it) }
            DebugLog.d("AssetParser", "Dictionary: inserted ${charactersToInsert.size} characters")
        }
    }

    suspend fun parseAndSaveGraphics(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            val reader = context.assets.open("graphics.txt").bufferedReader()
            val gson = Gson()
            val graphicsToInsert = mutableListOf<HanziGraphics>()

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val json = gson.fromJson(line, JsonObject::class.java)
                        val character = json.get("character")?.asString ?: return@forEach
                        graphicsToInsert.add(HanziGraphics(character = character, rawJson = line))
                    } catch (_: Exception) { }
                }
            }
            graphicsToInsert.chunked(1000).forEach { dao.insertGraphics(it) }
            DebugLog.d("AssetParser", "Graphics: inserted ${graphicsToInsert.size} entries")
        }
    }

    suspend fun parseAndSaveRadicals(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            val reader = context.assets.open("dictionary.txt").bufferedReader()
            val gson = Gson()
            var count = 0

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val json = gson.fromJson(line, JsonObject::class.java)
                        val character = json.get("character")?.asString ?: return@forEach
                        val radical = json.get("radical")?.asString ?: ""
                        val decomposition = json.get("decomposition")?.asString ?: ""
                        dao.updateRadicalAndDecomp(character, radical, decomposition)
                        count++
                    } catch (_: Exception) { }
                }
            }
            DebugLog.d("AssetParser", "Radicals/Decomp: updated $count entries")
        }
    }
}
