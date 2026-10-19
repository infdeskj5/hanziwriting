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
import com.infdesk.hanziwriting.utils.HanziTransform
import com.infdesk.hanziwriting.utils.ProgressStore
import com.infdesk.hanziwriting.utils.StrokeEvaluator
import kotlinx.coroutines.launch

data class CharacterPracticeData(
    val character: String,
    val pinyin: String,
    val definition: String,
    val radical: String?,
    val totalStrokes: Int,
    val medians: List<List<List<Double>>>,
    val strokes: List<String>   // SVG path data for the beautiful stroke shapes
)

class PracticeViewModel(private val database: AppDatabase) : ViewModel() {
    var practiceData by mutableStateOf<CharacterPracticeData?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    // 0 = full guide, 1 = faded guide, 2 = hidden guide, 3 = memory mode
    var guideLevel by mutableIntStateOf(0)
        private set

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

    fun loadCharacter(character: String) {
        loadError = null
        practiceData = null
        viewModelScope.launch {
            try {
                val charData = database.hanziDao().getCharacterExact(character)
                val graphicsData = database.hanziDao().getGraphics(character)
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

            @Suppress("UNCHECKED_CAST")
            val medians = jsonMap["medians"] as? List<List<List<Double>>>
            if (medians.isNullOrEmpty()) {
                loadError = "No median stroke data for '$character'"
                return
            }
            @Suppress("UNCHECKED_CAST")
            val strokes = (jsonMap["strokes"] as? List<String>) ?: emptyList()

            val successCount = ProgressStore.getSuccessCount(character)
            guideLevel = computeGuideLevel(successCount)

            practiceData = CharacterPracticeData(
                character = character,
                pinyin = charData.pinyin,
                definition = charData.definition,
                radical = charData.radical,
                totalStrokes = medians.size,
                medians = medians,
                strokes = strokes
            )
            resetPractice()
        } catch (e: Exception) {
            loadError = "Parse failed: ${e.message}"
            DebugLog.e("PracticeVM", "parseGraphicsAndSetup failed", e)
        }
    }

    private fun computeGuideLevel(successCount: Int): Int = when {
        successCount <= 0 -> 0   // full guide
        successCount == 1 -> 1   // faded guide
        successCount == 2 -> 2   // hidden guide, character shown as reference
        else -> 3               // memory mode: no guide, character hidden
    }

    fun addPointToCurrentStroke(point: Offset) {
        currentStrokePoints.add(point)
    }

    fun finishStroke(canvasSize: Float) {
        val data = practiceData ?: return
        if (currentStrokeIndex >= data.medians.size) return

        val scale = canvasSize / HanziTransform.GRID_WIDTH
        val expectedCanvasPoints = data.medians[currentStrokeIndex].map { HanziTransform.toCanvas(it, scale) }

        // Forgiving evaluation: generous threshold + allow over half the points to be off
        val errors = StrokeEvaluator.evaluateStroke(
            userPoints = currentStrokePoints.toList(),
            expectedPoints = expectedCanvasPoints,
            threshold = canvasSize * 0.15f
        )
        val maxAllowedErrors = (currentStrokePoints.size * 0.55f).toInt()

        if (errors <= maxAllowedErrors && currentStrokePoints.size > 3) {
            completedStrokes.add(currentStrokePoints.toList())
            currentStrokePoints.clear()
            currentStrokeIndex++
            if (currentStrokeIndex >= data.totalStrokes) {
                isCompleted = true
                ProgressStore.incrementSuccess(data.character)
            }
        } else {
            totalErrors++
            currentStrokePoints.clear()
        }
    }

    // Re-practice the current character at its (now higher) difficulty
    fun practiceAgain() {
        val data = practiceData ?: return
        val successCount = ProgressStore.getSuccessCount(data.character)
        guideLevel = computeGuideLevel(successCount)
        resetPractice()
    }

    fun resetPractice() {
        currentStrokeIndex = 0
        totalErrors = 0
        isCompleted = false
        currentStrokePoints.clear()
        completedStrokes.clear()
    }
}
