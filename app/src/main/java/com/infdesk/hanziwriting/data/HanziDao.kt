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

    /** Robust substring search used by SearchScreen. This does not depend on the
     * optional script-conversion tables, so a missing vocabulary metadata import
     * can never suppress ordinary Hanzi/pinyin/English results. Tone digits are
     * ignored for pinyin matching, allowing both ni3 and ni to match. */
    @Query("""
        SELECT * FROM hanzi_characters
        WHERE character LIKE '%' || :query || '%'
           OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(pinyin), '1', ''), '2', ''), '3', ''), '4', ''), '5', '')
              LIKE '%' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(:query), '1', ''), '2', ''), '3', ''), '4', ''), '5', '') || '%'
           OR LOWER(definition) LIKE '%' || LOWER(:query) || '%'
        ORDER BY CASE WHEN character = :query THEN 0 ELSE 1 END,
                 CASE WHEN LOWER(pinyin) = LOWER(:query) THEN 0 ELSE 1 END,
                 character
        LIMIT 50
    """)
    suspend fun searchHanziForSearch(query: String): List<HanziCharacter>

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

    // rowid preserves the order in which curriculum/list entries were inserted.
    @Query("SELECT character FROM list_items WHERE listId = :listId ORDER BY rowid ASC")
    suspend fun getCharactersInListInInsertionOrder(listId: Int): List<String>

    @Query("SELECT COUNT(*) FROM list_items WHERE listId = :listId")
    suspend fun getListItemCount(listId: Int): Int

    @Query("SELECT character FROM list_items WHERE listId = :listId ORDER BY character LIMIT :limit OFFSET :offset")
    suspend fun getCharactersInListPage(listId: Int, limit: Int, offset: Int): List<String>

    // Used by AddToListDialog to show which practice lists already contain an item.
    @Query("SELECT listId FROM list_items WHERE character = :character ORDER BY listId")
    suspend fun getListIdsContainingCharacter(character: String): List<Int>

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
