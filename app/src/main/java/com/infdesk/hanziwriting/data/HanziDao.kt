package com.infdesk.hanziwriting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    // Inserts ONLY if the character is missing (keeps the CC-CEDICT row when present).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCharacter(character: HanziCharacter)

    @Query("UPDATE hanzi_characters SET radical = :radical, decomposition = :decomposition, matchesJson = :matchesJson, mmaDefinition = :mmaDefinition WHERE character = :character")
    suspend fun updateRadicalAndDecomp(
        character: String,
        radical: String,
        decomposition: String,
        matchesJson: String,
        mmaDefinition: String
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacters(characters: List<HanziCharacter>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGraphics(graphics: List<HanziGraphics>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(list: PracticeList): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCharacterToList(item: ListItem)

    @Query("SELECT * FROM practice_lists ORDER BY createdAt DESC, listId DESC")
    suspend fun getAllLists(): List<PracticeList>

    @Query("SELECT * FROM practice_lists WHERE name = :name ORDER BY listId LIMIT 1")
    suspend fun getListByName(name: String): PracticeList?

    @Query("SELECT character FROM list_items WHERE listId = :listId")
    suspend fun getCharactersInList(listId: Int): List<String>

    @Query("DELETE FROM list_items WHERE listId = :listId")
    suspend fun deleteListItems(listId: Int)

    @Query("DELETE FROM practice_lists WHERE listId = :listId")
    suspend fun deletePracticeList(listId: Int)

    @Query("DELETE FROM hanzi_characters")
    suspend fun clearCharacters()

    @Query("DELETE FROM hanzi_graphics")
    suspend fun clearGraphics()
}
