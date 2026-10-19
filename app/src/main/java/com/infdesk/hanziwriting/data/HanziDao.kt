package com.infdesk.hanziwriting.data

import androidx.room.*

@Dao
interface HanziDao {
    @Query("SELECT * FROM hanzi_characters WHERE character = :query OR pinyin LIKE :query OR definition LIKE :query LIMIT 50")
    suspend fun searchHanzi(query: String): List<HanziCharacter>

    @Query("SELECT * FROM hanzi_characters WHERE character = :query OR pinyin LIKE :query OR definition LIKE :query LIMIT 50")
    suspend fun searchHanziExact(query: String): List<HanziCharacter>

    @Query("SELECT * FROM hanzi_characters WHERE character = :character LIMIT 1")
    suspend fun getCharacterExact(character: String): HanziCharacter?

    @Query("SELECT * FROM hanzi_graphics WHERE character = :character")
    suspend fun getGraphics(character: String): HanziGraphics?

    @Query("UPDATE hanzi_characters SET radical = :radical, decomposition = :decomposition WHERE character = :character")
    suspend fun updateRadicalAndDecomp(character: String, radical: String, decomposition: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacters(characters: List<HanziCharacter>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGraphics(graphics: List<HanziGraphics>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(list: PracticeList): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCharacterToList(item: ListItem)

    @Query("SELECT * FROM practice_lists")
    suspend fun getAllLists(): List<PracticeList>

    @Query("SELECT character FROM list_items WHERE listId = :listId")
    suspend fun getCharactersInList(listId: Int): List<String>

    @Query("DELETE FROM hanzi_characters")
    suspend fun clearCharacters()

    @Query("DELETE FROM hanzi_graphics")
    suspend fun clearGraphics()
}
