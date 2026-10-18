package com.infdesk.hanziwriting.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
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

            val regex = Regex("^(.+?)\\s+(.+?)\\s+\\[(.+?)\\]\\s+/(.+)/$")

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#") || line.isBlank()) return@forEach
                    val match = regex.find(line)
                    if (match != null) {
                        val (traditional, simplified, pinyin, definition) = match.destructured
                        charactersToInsert.add(
                            HanziCharacter(
                                character = simplified,
                                pinyin = pinyin,
                                definition = definition.replace("/", ", "),
                                radical = ""
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
                                rawJson = line
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

    suspend fun parseAndSaveRadicals(context: Context, dao: HanziDao) {
        withContext(Dispatchers.IO) {
            val inputStream = context.assets.open("dictionary.txt")
            val reader = inputStream.bufferedReader()
            val gson = Gson()

            reader.useLines { lines ->
                lines.chunked(500).forEach { chunk ->
                    chunk.forEach { line ->
                        if (line.isBlank()) return@forEach
                        try {
                            val json = gson.fromJson(line, JsonObject::class.java)
                            val character = json.get("character")?.asString ?: return@forEach
                            val radical = json.get("radical")?.asString ?: ""
                            dao.updateRadical(character, radical)
                        } catch (e: Exception) {
                            // Ignore malformed lines
                        }
                    }
                }
            }
        }
    }
}
