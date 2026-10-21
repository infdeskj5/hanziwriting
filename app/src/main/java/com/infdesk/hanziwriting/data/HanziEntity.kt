package com.infdesk.hanziwriting.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hanzi_characters")
data class HanziCharacter(
    @PrimaryKey val character: String,
    val pinyin: String,
    val definition: String,
    val radical: String = "",
    val decomposition: String = "",
    val matchesJson: String = "" // Stores the JSON array of paths
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
