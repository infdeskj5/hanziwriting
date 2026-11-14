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

    @Query("SELECT * FROM hanzi_graphics WHERE character = :character LIMIT 1")
    suspend fun getGraphics(character: String): HanziGraphics?

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCharactersToList(items: List<ListItem>)

    @Query("SELECT * FROM practice_lists ORDER BY createdAt, listId")
    suspend fun getAllLists(): List<PracticeList>

    @Query("SELECT * FROM practice_lists WHERE name = :name LIMIT 1")
    suspend fun getListByName(name: String): PracticeList?

    @Query("SELECT character FROM list_items WHERE listId = :listId")
    suspend fun getCharactersInList(listId: Int): List<String>

    @Query("DELETE FROM list_items WHERE listId = :listId")
    suspend fun deleteListItems(listId: Int)

    @Query("DELETE FROM list_items WHERE listId = :listId AND character IN (:characters)")
    suspend fun deleteSelectedListItems(listId: Int, characters: List<String>)

    @Query("DELETE FROM practice_lists WHERE listId = :listId")
    suspend fun deletePracticeList(listId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordForms(forms: List<HanziWordForm>)

    @Query("SELECT * FROM hanzi_word_forms WHERE simplified = :word OR traditional = :word ORDER BY simplified, traditional")
    suspend fun getWordForms(word: String): List<HanziWordForm>

    @Query("SELECT DISTINCT simplified FROM hanzi_word_forms WHERE traditional = :traditional ORDER BY simplified")
    suspend fun getSimplifiedForms(traditional: String): List<String>

    @Query("SELECT DISTINCT traditional FROM hanzi_word_forms WHERE simplified = :simplified ORDER BY traditional")
    suspend fun getTraditionalForms(simplified: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVocabularyEntries(entries: List<VocabularyEntry>)

    @Query("SELECT DISTINCT simplified, traditional, level FROM vocabulary_entries WHERE source = 'HSK' AND (simplified IN (:words) OR traditional IN (:words)) ORDER BY simplified, level")
    suspend fun getHskLevelsForWords(words: List<String>): List<WordLevelTag>

    @Query("SELECT DISTINCT level FROM vocabulary_entries WHERE source = 'HSK' AND (simplified = :word OR traditional = :word) ORDER BY level")
    suspend fun getHskLevelsForWord(word: String): List<String>

    @Query("SELECT preferredForm FROM vocabulary_entries WHERE source = :source AND level = :level ORDER BY sortOrder, entryId")
    suspend fun getVocabularyWords(source: String, level: String): List<String>

    @Query("SELECT * FROM study_list_bindings")
    suspend fun getStudyListBindings(): List<StudyListBinding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyListBinding(binding: StudyListBinding)

    @Query("DELETE FROM study_list_bindings WHERE source = :source AND level = :level")
    suspend fun deleteStudyListBinding(source: String, level: String)

    @Query("DELETE FROM hanzi_characters")
    suspend fun clearCharacters()

    @Query("DELETE FROM hanzi_graphics")
    suspend fun clearGraphics()
}
