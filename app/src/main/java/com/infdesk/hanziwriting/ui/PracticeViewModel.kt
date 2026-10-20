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

enum class PracticePhase {
    ANIMATION,          // Step 1: Watch animation
    TRACE_WITH_TEMPLATE,// Step 2: Draw with template
    STRUCTURE_QUIZ,     // Step 3: Select components/radicals
    TRACE_BLIND_TEMPLATE,// Step 4: Draw with template, no char at top
    MEMORY_DRAW         // Step 5: Draw from memory
}

data class CharacterProgress(
    val character: String,
    var currentPhase: PracticePhase = PracticePhase.ANIMATION,
    var isPhaseComplete: Boolean = false,
    var selectedComponents: List<String> = emptyList(),
    var charData: HanziCharacter? = null,
    var medians: List<List<List<Double>>> = emptyList(),
    var strokes: List<String> = emptyList()
)

class PracticeViewModel(private val database: AppDatabase) : ViewModel() {
    val characters = mutableStateListOf<CharacterProgress>()
    
    var globalPhaseIndex by mutableIntStateOf(0)
        private set
    var currentCharIndex by mutableIntStateOf(0)
        private set
        
    val phases = listOf(
        PracticePhase.ANIMATION,
        PracticePhase.TRACE_WITH_TEMPLATE,
        PracticePhase.STRUCTURE_QUIZ,
        PracticePhase.TRACE_BLIND_TEMPLATE,
        PracticePhase.MEMORY_DRAW
    )
    
    val currentPhase: PracticePhase get() = phases[globalPhaseIndex]
    val currentChar: CharacterProgress? get() = characters.getOrNull(currentCharIndex)
    
    var isAllComplete by mutableStateOf(false)
        private set

    var currentStrokeIndex by mutableIntStateOf(0)
        private set
    var totalErrors by mutableIntStateOf(0)
        private set
    
    val currentStrokePoints = mutableStateListOf<Offset>()
    val completedStrokes = mutableStateListOf<List<Offset>>()

    private val gson = Gson()

    fun loadCharacters(input: String) {
        characters.clear()
        input.forEach { char ->
            characters.add(CharacterProgress(character = char.toString()))
        }
        if (characters.isEmpty()) return
        
        viewModelScope.launch {
            characters.forEachIndexed { index, cp ->
                val charData = database.hanziDao().getCharacterExact(cp.character)
                val graphicsData = database.hanziDao().getGraphics(cp.character)
                
                if (charData != null && graphicsData != null) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val jsonMap: Map<String, Any> = gson.fromJson(graphicsData.rawJson, type)
                    @Suppress("UNCHECKED_CAST")
                    val medians = jsonMap["medians"] as? List<List<List<Double>>> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val strokes = (jsonMap["strokes"] as? List<String>) ?: emptyList()
                    
                    characters[index] = cp.copy(
                        charData = charData,
                        medians = medians,
                        strokes = strokes
                    )
                }
            }
        }
    }

    fun onPhaseComplete() {
        if (currentChar == null) return
        
        // Mark current char's phase as complete
        characters[currentCharIndex] = currentChar!!.copy(isPhaseComplete = true)
        
        // Move to next character in the SAME phase
        if (currentCharIndex < characters.lastIndex) {
            currentCharIndex++
            resetStrokeState()
        } else {
            // All characters finished this phase, move to next phase
            if (globalPhaseIndex < phases.lastIndex) {
                globalPhaseIndex++
                currentCharIndex = 0
                // Reset phase complete flags for all chars
                for (i in characters.indices) {
                    characters[i] = characters[i].copy(isPhaseComplete = false)
                }
                resetStrokeState()
            } else {
                // All phases complete!
                isAllComplete = true
                ProgressStore.incrementSuccess(characters.first().character) // Increment for the word
            }
        }
    }

    fun selectComponent(component: String) {
        val cp = currentChar ?: return
        val idsSymbols = setOf('⿰', '⿱', '⿲', '⿳', '⿴', '⿵', '⿶', '⿷', '⿸', '⿹', '⿺', '⿻', '？', ' ')
        val correctComponents = cp.charData?.decomposition?.filter { it !in idsSymbols }?.map { it.toString() }?.distinct() ?: emptyList()
        
        val newSelected = cp.selectedComponents + component
        characters[currentCharIndex] = cp.copy(selectedComponents = newSelected)
        
        if (newSelected.size == correctComponents.size) {
            // Quiz complete for this character
            onPhaseComplete()
        }
    }

    fun addPointToCurrentStroke(point: Offset) {
        currentStrokePoints.add(point)
    }

    fun finishStroke(canvasSize: Float) {
        val cp = currentChar ?: return
        if (currentStrokeIndex >= cp.medians.size) return

        val scale = canvasSize / HanziTransform.GRID_WIDTH
        val expectedCanvasPoints = cp.medians[currentStrokeIndex].map { HanziTransform.toCanvas(it, scale) }

        val isCorrect = StrokeEvaluator.evaluateStroke(
            userPoints = currentStrokePoints.toList(),
            expectedPoints = expectedCanvasPoints,
            canvasSize = canvasSize
        )

        if (isCorrect) {
            completedStrokes.add(currentStrokePoints.toList())
            currentStrokePoints.clear()
            currentStrokeIndex++
            if (currentStrokeIndex >= cp.medians.size) {
                onPhaseComplete()
            }
        } else {
            totalErrors++
            currentStrokePoints.clear()
        }
    }

    private fun resetStrokeState() {
        currentStrokeIndex = 0
        totalErrors = 0
        currentStrokePoints.clear()
        completedStrokes.clear()
    }
}
