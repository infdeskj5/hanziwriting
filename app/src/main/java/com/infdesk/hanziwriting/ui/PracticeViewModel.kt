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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CharacterPracticeData(
    val character: String,
    val pinyin: String,
    val definition: String,
    val radical: String?,
    val components: List<String>, // Extracted from decomposition
    val totalStrokes: Int,
    val medians: List<List<List<Double>>>,
    val strokes: List<String>
)

class PracticeViewModel(private val database: AppDatabase) : ViewModel() {
    var practiceData by mutableStateOf<CharacterPracticeData?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

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
    var mistakesOnCurrentStroke by mutableIntStateOf(0)
        private set
    var totalErrors by mutableIntStateOf(0)
        private set
    var isCompleted by mutableStateOf(false)
        private set

    // Animation state
    var animatedStrokeIndex by mutableIntStateOf(-1)
        private set
    var animationProgress by mutableStateOf(1f)
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
                    playAnimation() // Auto-play animation on load
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

            // Extract components from IDS decomposition string
            val idsSymbols = setOf('⿰', '⿱', '⿲', '⿳', '⿴', '⿵', '⿶', '⿷', '⿸', '⿹', '⿺', '⿻', '？', ' ')
            val components = charData.decomposition.filter { it !in idsSymbols }.map { it.toString() }.distinct()

            val successCount = ProgressStore.getSuccessCount(character)
            guideLevel = computeGuideLevel(successCount)

            practiceData = CharacterPracticeData(
                character = character,
                pinyin = charData.pinyin,
                definition = charData.definition,
                radical = charData.radical,
                components = components,
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
        successCount <= 0 -> 0   // Full guide
        successCount == 1 -> 1   // Faded guide
        successCount == 2 -> 2   // Hidden guide
        else -> 3               // Memory mode (no guide, no character text)
    }

    fun playAnimation() {
        viewModelScope.launch {
            val data = practiceData ?: return@launch
            for (i in 0 until data.totalStrokes) {
                animatedStrokeIndex = i
                animationProgress = 0f
                delay(50) // Allow recomposition to catch up
                animationProgress = 1f
                delay(650) // Animation duration + pause
            }
            animatedStrokeIndex = -1
        }
    }

    fun addPointToCurrentStroke(point: Offset) {
        currentStrokePoints.add(point)
    }

    fun finishStroke(canvasSize: Float) {
        val data = practiceData ?: return
        if (currentStrokeIndex >= data.medians.size) return

        val scale = canvasSize / HanziTransform.GRID_WIDTH
        val expectedCanvasPoints = data.medians[currentStrokeIndex].map { HanziTransform.toCanvas(it, scale) }

        val isCorrect = StrokeEvaluator.evaluateStroke(
            userPoints = currentStrokePoints.toList(),
            expectedPoints = expectedCanvasPoints,
            canvasSize = canvasSize
        )

        if (isCorrect) {
            completedStrokes.add(currentStrokePoints.toList())
            currentStrokePoints.clear()
            currentStrokeIndex++
            mistakesOnCurrentStroke = 0
            if (currentStrokeIndex >= data.totalStrokes) {
                isCompleted = true
                ProgressStore.incrementSuccess(data.character)
            }
        } else {
            mistakesOnCurrentStroke++
            totalErrors++
            currentStrokePoints.clear()
        }
    }

    fun practiceAgain() {
        val data = practiceData ?: return
        val successCount = ProgressStore.getSuccessCount(data.character)
        guideLevel = computeGuideLevel(successCount)
        resetPractice()
    }

    fun resetPractice() {
        currentStrokeIndex = 0
        totalErrors = 0
        mistakesOnCurrentStroke = 0
        isCompleted = false
        currentStrokePoints.clear()
        completedStrokes.clear()
    }
}
