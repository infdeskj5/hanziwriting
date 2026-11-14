package com.infdesk.hanziwriting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "hanzi_characters")
data class HanziCharacter(
    @PrimaryKey val character: String,
    val pinyin: String,
    val definition: String,
    val radical: String = "",
    val decomposition: String = "",
    val matchesJson: String = "",
    val mmaDefinition: String = ""
)

@Entity(tableName = "hanzi_graphics")
data class HanziGraphics(
    @PrimaryKey val character: String,
    val rawJson: String
)

@Entity(tableName = "practice_lists")
data class PracticeList(
    @PrimaryKey(autoGenerate = true) val listId: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "list_items", primaryKeys = ["listId", "character"])
data class ListItem(
    val listId: Int,
    val character: String
)

@Entity(
    tableName = "hanzi_word_forms",
    primaryKeys = ["simplified", "traditional"],
    indices = [Index(value = ["traditional"])]
)
data class HanziWordForm(
    val simplified: String,
    val traditional: String
)

@Entity(
    tableName = "vocabulary_entries",
    primaryKeys = ["source", "entryId"],
    indices = [
        Index(value = ["source", "level"]),
        Index(value = ["simplified"]),
        Index(value = ["traditional"])
    ]
)
data class VocabularyEntry(
    val source: String,
    val entryId: String,
    val level: String,
    val simplified: String,
    val traditional: String,
    val preferredForm: String,
    val pinyin: String,
    val partOfSpeech: String,
    val sortOrder: Int
)

@Entity(
    tableName = "study_list_bindings",
    primaryKeys = ["source", "level"],
    indices = [Index(value = ["listId"])]
)
data class StudyListBinding(
    val source: String,
    val level: String,
    val listId: Int
)

/** Lightweight Room projection used by Search and Word Details. */
data class WordLevelTag(
    val simplified: String,
    val traditional: String,
    val level: String
)
