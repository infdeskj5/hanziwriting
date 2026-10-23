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
import com.infdesk.hanziwriting.utils.HanziTransform
import com.infdesk.hanziwriting.utils.IdsNode
import com.infdesk.hanziwriting.utils.IdsTreeBuilder
import com.infdesk.hanziwriting.utils.ProgressStore
import com.infdesk.hanziwriting.utils.StrokeEvaluator
import kotlinx.coroutines.launch

enum class PracticePhase { ANIMATION, TRACE_WITH_TEMPLATE, STRUCTURE_QUIZ, TRACE_BLIND_TEMPLATE, MEMORY_DRAW }

data class ComponentInfo(val char: Char, val leafIndex: Int, val order: Int)

data class CharacterProgress(
    val character: String,
    val charData: HanziCharacter? = null,
    val medians: List<List<List<Double>>> = emptyList(),
    val strokes: List<String> = emptyList(),
    val orderedComponents: List<ComponentInfo> = emptyList(),
    val idsRoot: IdsNode? = null,
    val leafRank: Map<Int, Int> = emptyMap()
) {
    fun getPhases(): List<PracticePhase> {
        val list = mutableListOf(PracticePhase.ANIMATION, PracticePhase.TRACE_WITH_TEMPLATE)
        if (orderedComponents.size > 1) list.add(PracticePhase.STRUCTURE_QUIZ)
        list.add(PracticePhase.TRACE_BLIND_TEMPLATE)
        list.add(PracticePhase.MEMORY_DRAW)
        return list
    }
}

class PracticeViewModel(private val database: AppDatabase) : ViewModel() {
    val characters = mutableStateListOf<CharacterProgress>()

    val allPossiblePhases = listOf(
        PracticePhase.ANIMATION, PracticePhase.TRACE_WITH_TEMPLATE,
        PracticePhase.STRUCTURE_QUIZ, PracticePhase.TRACE_BLIND_TEMPLATE, PracticePhase.MEMORY_DRAW
    )

    var currentGlobalPhaseIndex by mutableIntStateOf(0); private set
    var currentCharIndex by mutableIntStateOf(0); private set
    val currentGlobalPhase: PracticePhase get() = allPossiblePhases[currentGlobalPhaseIndex]
    val currentChar: CharacterProgress? get() = characters.getOrNull(currentCharIndex)

    var isAllComplete by mutableStateOf(false); private set
    var waitingForNext by mutableStateOf(false); private set

    var currentStrokeIndex by mutableIntStateOf(0); private set
    var totalErrors by mutableIntStateOf(0); private set

    val currentStrokePoints = mutableStateListOf<Offset>()
    val completedStrokes = mutableStateListOf<List<Offset>>()
    val selectedComponents = mutableStateListOf<String>()
    // FIX #3: track used option buttons by unique ID (handles duplicate radicals)
    val usedOptionIds = mutableStateListOf<Int>()

    private val gson = Gson()

    fun loadCharacters(input: String) {
        characters.clear()
        input.forEach { characters.add(CharacterProgress(character = it.toString())) }
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

                    val buildResult = IdsTreeBuilder(database.hanziDao()).build(cp.character, medians.size)
                    val orderedComponents = buildResult.atomicLeaves
                        .map { ComponentInfo(it.char, it.index, buildResult.leafRank[it.index] ?: Int.MAX_VALUE) }
                        .sortedBy { it.order }

                    characters[index] = cp.copy(
                        charData = charData, medians = medians, strokes = strokes,
                        orderedComponents = orderedComponents,
                        idsRoot = buildResult.root, leafRank = buildResult.leafRank
                    )
                }
            }
            while (currentCharIndex < characters.size &&
                !characters[currentCharIndex].getPhases().contains(currentGlobalPhase)) currentCharIndex++
        }
    }

    fun onPhaseTaskComplete() {
        if (isAbsoluteLastStep()) {
            isAllComplete = true
            ProgressStore.incrementSuccess(characters.first().character)
        } else {
            waitingForNext = true
        }
    }

    fun proceedNext() {
        waitingForNext = false
        advancePhase()
    }

    private fun isAbsoluteLastStep(): Boolean =
        currentGlobalPhaseIndex == allPossiblePhases.lastIndex && currentCharIndex == characters.lastIndex

    private fun advancePhase() {
        var nextIndex = currentCharIndex + 1
        while (nextIndex < characters.size) {
            if (characters[nextIndex].getPhases().contains(currentGlobalPhase)) break
            nextIndex++
        }
        if (nextIndex < characters.size) {
            currentCharIndex = nextIndex
        } else {
            if (currentGlobalPhaseIndex < allPossiblePhases.lastIndex) {
                currentGlobalPhaseIndex++
                currentCharIndex = 0
                while (currentCharIndex < characters.size &&
                    !characters[currentCharIndex].getPhases().contains(currentGlobalPhase)) currentCharIndex++
                if (currentCharIndex >= characters.size) advancePhase()
            } else {
                isAllComplete = true
                ProgressStore.incrementSuccess(characters.first().character)
            }
        }
        resetStrokeState()
    }

    // FIX #3: options are identified by unique ID so duplicate radicals work correctly
    fun selectComponent(optionId: Int, component: String): Boolean {
        val cp = currentChar ?: return false
        val expectedIndex = selectedComponents.size
        if (expectedIndex >= cp.orderedComponents.size) return false
        if (usedOptionIds.contains(optionId)) return false
        return if (component == cp.orderedComponents[expectedIndex].char.toString()) {
            selectedComponents.add(component)
            usedOptionIds.add(optionId)
            if (selectedComponents.size == cp.orderedComponents.size) onPhaseTaskComplete()
            true
        } else {
            totalErrors++
            false
        }
    }

    fun addPointToCurrentStroke(point: Offset) { currentStrokePoints.add(point) }

    fun finishStroke(canvasSize: Float) {
        val cp = currentChar ?: return
        if (currentStrokeIndex >= cp.medians.size) return
        val scale = canvasSize / HanziTransform.GRID_WIDTH
        val expected = cp.medians[currentStrokeIndex].map { HanziTransform.toCanvas(it, scale) }
        val isCorrect = StrokeEvaluator.evaluateStroke(currentStrokePoints.toList(), expected, canvasSize)
        if (isCorrect) {
            completedStrokes.add(currentStrokePoints.toList())
            currentStrokePoints.clear()
            currentStrokeIndex++
            if (currentStrokeIndex >= cp.medians.size) onPhaseTaskComplete()
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
        selectedComponents.clear()
        usedOptionIds.clear()
    }
}
