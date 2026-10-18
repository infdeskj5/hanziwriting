package com.infdesk.hanziwriting.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.utils.DebugLog
import com.infdesk.hanziwriting.utils.StrokeEvaluator
import kotlinx.coroutines.launch

data class CharacterPracticeData(
    val character: String,
    val pinyin: String,
    val definition: String,
    val radical: String?,
    val totalStrokes: Int,
    val medians: List<List<List<Double>>>
)

class PracticeViewModel(private val database: AppDatabase) : ViewModel() {
    var practiceData by mutableStateOf<CharacterPracticeData?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    // Queue of characters (a word is split into its characters)
    private var characterQueue = listOf<String>()
    var totalCharacters by mutableIntStateOf(0)
        private set
    var currentQueueIndex by mutableIntStateOf(0)
        private set

    val hasNextCharacter: Boolean get() = currentQueueIndex < totalCharacters - 1
    val hasPreviousCharacter: Boolean get() = currentQueueIndex > 0

    var currentStrokeIndex by mutableIntStateOf(0)
        private set
    var totalErrors by mutableIntStateOf(0)
        private set
    var isCompleted by mutableStateOf(false)
        private set

    val currentStrokePoints = mutableStateListOf<Offset>()
    val completedStrokes = mutableStateListOf<List<Offset>>()

    private val gson = Gson()

    // Entry point from SearchScreen: the string may be a single char OR a word
    fun loadCharacters(input: String) {
        characterQueue = input.map { it.toString() }
        totalCharacters = characterQueue.size
        currentQueueIndex = 0
        if (characterQueue.isEmpty()) {
            loadError = "No characters to practice"
            return
        }
        loadCharacter(characterQueue[currentQueueIndex])
    }

    fun nextCharacter() {
        if (hasNextCharacter) {
            currentQueueIndex++
            loadCharacter(characterQueue[currentQueueIndex])
        }
    }

    fun previousCharacter() {
        if (hasPreviousCharacter) {
            currentQueueIndex--
            loadCharacter(characterQueue[currentQueueIndex])
        }
    }

    // Used by both the queue and ListPracticeScreen
    fun loadCharacter(character: String) {
        loadError = null
        practiceData = null
        viewModelScope.launch {
            try {
                DebugLog.d("PracticeVM", "loadCharacter('$character')")
                val charData = database.hanziDao().getCharacterExact(character)
                DebugLog.d("PracticeVM", "charData = ${charData?.character ?: "NULL"}")
                val graphicsData = database.hanziDao().getGraphics(character)
                DebugLog.d("PracticeVM", "graphicsData = ${if (graphicsData != null) "FOUND" else "NULL"}")

                if (charData != null && graphicsData != null) {
                    parseGraphicsAndSetup(character, charData, graphicsData.rawJson)
                } else {
                    loadError = when {
                        charData == null -> "No dictionary entry for '$character'"
                        graphicsData == null -> "No stroke graphics for '$character'"
                        else -> "Unknown error"
                    }
                    DebugLog.e("PracticeVM", loadError ?: "Unknown")
                }
            } catch (e: Exception) {
                loadError = "Exception: ${e.message}"
                DebugLog.e("PracticeVM", "loadCharacter failed", e)
            }
        }
    }

    private fun parseGraphicsAndSetup(character: String, charData: HanziCharacter, rawJson: String) {
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = gson.fromJson(rawJson, type)

            // FIX: "strokes" in graphics.txt is an ARRAY of SVG paths, not a number.
            // The stroke count is the number of median lines.
            @Suppress("UNCHECKED_CAST")
            val medians = jsonMap["medians"] as? List<List<List<Double>>>
            if (medians.isNullOrEmpty()) {
                loadError = "No median stroke data for '$character'"
                DebugLog.e("PracticeVM", loadError ?: "No medians")
                return
            }
            val strokes = medians.size

            practiceData = CharacterPracticeData(
                character = character,
                pinyin = charData.pinyin,
                definition = charData.definition,
                radical = charData.radical,   // radical comes from dictionary.txt, not graphics.txt
                totalStrokes = strokes,
                medians = medians
            )
            DebugLog.d("PracticeVM", "Setup complete for '$character', strokes=$strokes")
            resetPractice()
        } catch (e: Exception) {
            loadError = "Parse failed: ${e.message}"
            DebugLog.e("PracticeVM", "parseGraphicsAndSetup failed", e)
        }
    }

    fun addPointToCurrentStroke(point: Offset) {
        currentStrokePoints.add(point)
    }

    fun finishStroke(canvasSize: Float) {
        val data = practiceData ?: return
        if (currentStrokeIndex >= data.medians.size) return

        val expectedMedian = gson.toJson(data.medians[currentStrokeIndex])
        val errors = StrokeEvaluator.evaluateStroke(
            userPoints = currentStrokePoints.toList(),
            expectedMedianJson = expectedMedian,
            canvasSize = canvasSize,
            threshold = 60f
        )
        val maxAllowedErrors = (currentStrokePoints.size * 0.4).toInt()

        if (errors <= maxAllowedErrors && currentStrokePoints.size > 5) {
            completedStrokes.add(currentStrokePoints.toList())
            currentStrokePoints.clear()
            currentStrokeIndex++
            if (currentStrokeIndex >= data.totalStrokes) isCompleted = true
        } else {
            totalErrors++
            currentStrokePoints.clear()
        }
    }

    fun resetPractice() {
        currentStrokeIndex = 0
        totalErrors = 0
        isCompleted = false
        currentStrokePoints.clear()
        completedStrokes.clear()
    }
}
