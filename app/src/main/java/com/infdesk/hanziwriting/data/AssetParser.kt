package com.infdesk.hanziwriting.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MakeMeAHanziData(
    val character: String,
    val medians: List<List<List<Double>>>
)

object AssetParser {
    suspend fun parseAndSaveDictionary(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            val inputStream = context.assets.open("cedict_ts.u8")
            val reader = inputStream.bufferedReader()
            val charactersToInsert = mutableListOf<HanziCharacter>()

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#") || line.isBlank()) return@forEach
                    
                    val match = Regex("^(.+)\\s+(.+)\\s+\\[(.+)\\]\\s+/(.+)/").find(line)
                    if (match != null) {
                        val (traditional, simplified, pinyin, definition) = match.destructured
                        charactersToInsert.add(
                            HanziCharacter(
                                character = simplified,
                                pinyin = pinyin,
                                definition = definition.replace("/", ", "),
                                radicals = ""
                            )
                        )
                    }
                }
            }
            charactersToInsert.chunked(1000).forEach { chunk ->
                dao.insertCharacters(chunk)
            }
        }
    }

    suspend fun parseAndSaveGraphics(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            val inputStream = context.assets.open("graphics.txt")
            val reader = inputStream.bufferedReader()
            val gson = Gson()
            val graphicsToInsert = mutableListOf<HanziGraphics>()

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val data = gson.fromJson(line, MakeMeAHanziData::class.java)
                        graphicsToInsert.add(
                            HanziGraphics(
                                character = data.character,
                                rawJson = line // FIXED: Changed from strokesJson to rawJson
                            )
                        )
                    } catch (e: Exception) {
                        // Ignore malformed lines
                    }
                }
            }
            graphicsToInsert.chunked(1000).forEach { chunk ->
                dao.insertGraphics(chunk)
            }
        }
    }
}
