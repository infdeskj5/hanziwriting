package com.infdesk.hanziwriting.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MakeMeAHanziData(
    val character: String,
    val medians: List<List<List<Double>>> // Simplified: we will just store the raw JSON string for now
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
                    
                    // CC-CEDICT format: Traditional Simplified [pin1 yin2] /def1/def2/
                    val match = Regex("^(.+)\\s+(.+)\\s+\\[(.+)\\]\\s+/(.+)/").find(line)
                    if (match != null) {
                        val (traditional, simplified, pinyin, definition) = match.destructured
                        // We primarily care about the simplified character for this app
                        charactersToInsert.add(
                            HanziCharacter(
                                character = simplified,
                                pinyin = pinyin,
                                definition = definition.replace("/", ", "),
                                radicals = "" // Can be expanded later with a radical mapping file
                            )
                        )
                    }
                }
            }
            // Insert in batches to avoid memory issues
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
                                strokesJson = line // Store raw JSON line for easy parsing later
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
