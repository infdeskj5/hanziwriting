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
import com.infdesk.hanziwriting.utils.StrokeEvaluator
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

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
    var currentStrokeIndex by mutableIntStateOf(0)
        private set
    var totalErrors by mutableIntStateOf(0)
        private set
    var isCompleted by mutableStateOf(false)
        private set
    
    val currentStrokePoints = mutableStateListOf<Offset>()
    val completedStrokes = mutableStateListOf<List<Offset>>()

    private val gson = Gson()

    fun loadCharacter(character: String) {
        viewModelScope.launch {
            val charData = database.hanziDao().searchHanzi(character).firstOrNull()
            val graphicsData = database.hanziDao().getGraphics(character)

            if (charData != null && graphicsData != null) {
                parseGraphicsAndSetup(character, charData, graphicsData.rawJson)
            }
        }
    }

    private fun parseGraphicsAndSetup(character: String, charData: HanziCharacter, rawJson: String) {
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = gson.fromJson(rawJson, type)
            
            val strokes = (jsonMap["strokes"] as Double).toInt()
            val radical = jsonMap["radical"] as? String
            @Suppress("UNCHECKED_CAST")
            val medians = jsonMap["medians"] as List<List<List<Double>>>

            practiceData = CharacterPracticeData(
                character = character,
                pinyin = charData.pinyin,
                definition = charData.definition,
                radical = radical,
                totalStrokes = strokes,
                medians = medians
            )
            resetPractice()
        } catch (e: Exception) {
            e.printStackTrace()
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
            
            if (currentStrokeIndex >= data.totalStrokes) {
                isCompleted = true
            }
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
