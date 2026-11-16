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
import com.infdesk.hanziwriting.utils.IdsNode
import com.infdesk.hanziwriting.utils.IdsParser
import com.infdesk.hanziwriting.utils.ProgressStore
import com.infdesk.hanziwriting.utils.StrokeEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PracticePhase {
    ANIMATION,
    TRACE_WITH_TEMPLATE,
    STRUCTURE_QUIZ,
    TRACE_BLIND_TEMPLATE,
    MEMORY_DRAW
}

data class ComponentInfo(
    val char: Char,
    val leafIndex: Int,
    val order: Int
)

data class CharacterProgress(
    val character: String,
    val charData: HanziCharacter? = null,
    val medians: List<List<List<Double>>> = emptyList(),
    val strokes: List<String> = emptyList(),
    val orderedComponents: List<ComponentInfo> = emptyList(),
    val idsRoot: IdsNode? = null,
    val leafRank: Map<Int, Int> = emptyMap(),
    // Tier 1 done: dictionary row + IDS tree + component count known, so phase
    // planning is valid even before stroke assets arrive.
    val planned: Boolean = false,
    // Tier 2 done: stroke graphics parsed and component order refined.
    val assetsLoaded: Boolean = false
) {
    fun getPhases(): List<PracticePhase> {
        val list = mutableListOf(
            PracticePhase.ANIMATION,
            PracticePhase.TRACE_WITH_TEMPLATE
        )
        if (orderedComponents.size > 1) {
            list.add(PracticePhase.STRUCTURE_QUIZ)
        }
        list.add(PracticePhase.TRACE_BLIND_TEMPLATE)
        list.add(PracticePhase.MEMORY_DRAW)
        return list
    }
}

class PracticeViewModel(
    private val database: AppDatabase,
    private val practiceCharactersIndividually: Boolean = false
) : ViewModel() {
    val characters = mutableStateListOf<CharacterProgress>()
    val allPossiblePhases = listOf(
        PracticePhase.ANIMATION,
        PracticePhase.TRACE_WITH_TEMPLATE,
        PracticePhase.STRUCTURE_QUIZ,
        PracticePhase.TRACE_BLIND_TEMPLATE,
        PracticePhase.MEMORY_DRAW
    )
    var currentGlobalPhaseIndex by mutableIntStateOf(0)
        private set
    var currentCharIndex by mutableIntStateOf(0)
        private set
    val currentGlobalPhase: PracticePhase
        get() = allPossiblePhases[
            currentGlobalPhaseIndex.coerceIn(0, allPossiblePhases.lastIndex)
        ]
    val currentChar: CharacterProgress?
        get() = characters.getOrNull(currentCharIndex)
    var isAllComplete by mutableStateOf(false)
        private set
    var waitingForNext by mutableStateOf(false)
        private set
    var currentStrokeIndex by mutableIntStateOf(0)
        private set
    var totalErrors by mutableIntStateOf(0)
        private set
    val currentStrokePoints = mutableStateListOf<Offset>()
    val completedStrokes = mutableStateListOf<List<Offset>>()
    val selectedComponents = mutableStateListOf<String>()
    val usedOptionIds = mutableStateListOf<Int>()

    private val gson = Gson()
    private var loadJob: Job? = null
    private var assetJob: Job? = null

    fun loadCharacters(input: String) {
        loadJob?.cancel()
        assetJob?.cancel()
        characters.clear()
        resetStrokeState()
        isAllComplete = false
        waitingForNext = false
        currentGlobalPhaseIndex = 0
        currentCharIndex = 0
        // Practice each distinct character once. Curriculum lists repeat glyphs
        // hundreds of times; the old loader queried and Gson-parsed graphics for
        // EVERY occurrence on the main thread, which froze the app for tens of
        // seconds on HSK/TOCFL-sized sets.
        val distinctCharacters = input.toList().map { it.toString() }.distinct()
        distinctCharacters.forEach { characters.add(CharacterProgress(character = it)) }
        if (characters.isEmpty()) {
            isAllComplete = true
            return
        }
        loadJob = viewModelScope.launch {
            // Tier 1 (planning): batched dictionary rows + cheap IDS parsing,
            // entirely off the main thread. No graphics JSON is touched here.
            val rowsByCharacter: Map<String, HanziCharacter> = withContext(Dispatchers.Default) {
                val dao = database.hanziDao()
                val rows = HashMap<String, HanziCharacter>(distinctCharacters.size * 2)
                distinctCharacters.chunked(300).forEach { chunk ->
                    dao.getCharactersBatch(chunk).forEach { rows[it.character] = it }
                }
                rows
            }
            val planned = withContext(Dispatchers.Default) {
                characters.toList().map { cp ->
                    val row = rowsByCharacter[cp.character]
                    if (row == null) {
                        cp.copy(planned = true, assetsLoaded = true)
                    } else {
                        val idsRoot = IdsParser.parse(row.decomposition)
                        val leaves = IdsParser.getLeavesWithIndex(idsRoot)
                        val ordered = leaves.mapIndexed { rank, leaf ->
                            ComponentInfo(char = leaf.char, leafIndex = leaf.index, order = rank)
                        }
                        cp.copy(
                            charData = row,
                            orderedComponents = ordered,
                            idsRoot = idsRoot,
                            leafRank = ordered.associate { it.leafIndex to it.order },
                            planned = true
                        )
                    }
                }
            }
            characters.clear()
            characters.addAll(planned)
            alignCurrentPosition()
        }
    }

    /**
     * Tier 2 (assets): heavy stroke graphics JSON + writing-order refinement for
     * the current character plus a small lookahead only. Everything else stays
     * unparsed until (and unless) it becomes current.
     */
    private fun scheduleAssetLoad(fromIndex: Int) {
        if (isAllComplete || characters.isEmpty()) return
        assetJob?.cancel()
        val startIndex = fromIndex.coerceIn(0, characters.lastIndex)
        assetJob = viewModelScope.launch {
            val dao = database.hanziDao()
            for (index in startIndex until minOf(startIndex + ASSET_LOOKAHEAD, characters.size)) {
                val cp = characters.getOrNull(index) ?: break
                if (cp.assetsLoaded) continue
                val graphics = withContext(Dispatchers.IO) { dao.getGraphics(cp.character) }
                val parsed = withContext(Dispatchers.Default) {
                    parseGraphics(cp.character, graphics?.rawJson)
                }
                val refined = withContext(Dispatchers.Default) { refineComponentOrder(cp) }
                val current = characters.getOrNull(index) ?: break
                characters[index] = current.copy(
                    medians = parsed?.first.orEmpty(),
                    strokes = parsed?.second.orEmpty(),
                    orderedComponents = refined.first,
                    leafRank = refined.second,
                    assetsLoaded = true
                )
            }
        }
    }

    private fun parseGraphics(
        character: String,
        rawJson: String?
    ): Pair<List<List<List<Double>>>, List<String>>? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = gson.fromJson(rawJson, type)
            @Suppress("UNCHECKED_CAST")
            val medians = jsonMap["medians"] as? List<List<List<Double>>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val strokes = jsonMap["strokes"] as? List<String> ?: emptyList()
            medians to strokes
        }.onFailure {
            DebugLog.e("PracticeVM", "graphics parse failed for $character", it)
        }.getOrNull()
    }

    /** Reorders components by first stroke appearance (matches.json), keeping
     *  IDS document order as fallback. Runs only for windowed characters. */
    private fun refineComponentOrder(
        cp: CharacterProgress
    ): Pair<List<ComponentInfo>, Map<Int, Int>> {
        val row = cp.charData ?: return cp.orderedComponents to cp.leafRank
        val idsRoot = cp.idsRoot ?: return cp.orderedComponents to cp.leafRank
        if (cp.orderedComponents.size < 2 || row.matchesJson.isBlank()) {
            return cp.orderedComponents to cp.leafRank
        }
        val firstAppearance = mutableMapOf<Int, Int>()
        try {
            val matchesType = object : TypeToken<List<List<Number>?>>() {}.type
            val matches: List<List<Number>?> = gson.fromJson(row.matchesJson, matchesType)
            matches.forEachIndexed { strokeIndex, path ->
                if (path != null) {
                    val leafIndex = IdsParser.resolvePath(idsRoot, path.map { it.toInt() })
                    if (leafIndex >= 0 && !firstAppearance.containsKey(leafIndex)) {
                        firstAppearance[leafIndex] = strokeIndex
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.e("PracticeVM", "matches parse failed", e)
            return cp.orderedComponents to cp.leafRank
        }
        val reranked = cp.orderedComponents
            .sortedBy { firstAppearance[it.leafIndex] ?: Int.MAX_VALUE }
            .mapIndexed { rank, info -> info.copy(order = rank) }
        return reranked to reranked.associate { it.leafIndex to it.order }
    }

    private fun alignCurrentPosition() {
        if (characters.isEmpty()) {
            isAllComplete = true
            return
        }
        if (practiceCharactersIndividually) {
            // Sequential mode: finish every available phase for this Hanzi before
            // moving to the next one. Characters without a structure quiz simply
            // skip that phase.
            while (currentCharIndex < characters.size) {
                val availablePhases = characters[currentCharIndex].getPhases()
                val nextPhaseIndex = (currentGlobalPhaseIndex..allPossiblePhases.lastIndex)
                    .firstOrNull { allPossiblePhases[it] in availablePhases }
                if (nextPhaseIndex != null) {
                    currentGlobalPhaseIndex = nextPhaseIndex
                    scheduleAssetLoad(currentCharIndex)
                    return
                }
                currentCharIndex++
                currentGlobalPhaseIndex = 0
            }
            isAllComplete = true
            return
        }
        // Original interleaved mode: complete a phase across the whole set before
        // advancing to the next phase.
        while (
            currentCharIndex < characters.size &&
            !characters[currentCharIndex].getPhases().contains(currentGlobalPhase)
        ) {
            currentCharIndex++
        }
        while (
            currentCharIndex >= characters.size &&
            currentGlobalPhaseIndex < allPossiblePhases.lastIndex
        ) {
            currentGlobalPhaseIndex++
            currentCharIndex = 0
            while (
                currentCharIndex < characters.size &&
                !characters[currentCharIndex].getPhases().contains(currentGlobalPhase)
            ) {
                currentCharIndex++
            }
        }
        if (currentCharIndex >= characters.size) {
            isAllComplete = true
        } else {
            scheduleAssetLoad(currentCharIndex)
        }
    }

    fun onPhaseTaskComplete() {
        if (characters.isEmpty()) {
            isAllComplete = true
            return
        }
        if (isAbsoluteLastStep()) {
            isAllComplete = true
            currentChar?.let {
                ProgressStore.incrementSuccess(it.character)
            }
        } else {
            waitingForNext = true
        }
    }

    fun proceedNext() {
        waitingForNext = false
        advancePhase()
    }

    private fun isAbsoluteLastStep(): Boolean {
        if (practiceCharactersIndividually) {
            val phasesForCurrentCharacter = currentChar?.getPhases().orEmpty()
            return currentCharIndex == characters.lastIndex &&
                phasesForCurrentCharacter.lastOrNull() == currentGlobalPhase
        }
        if (currentGlobalPhaseIndex != allPossiblePhases.lastIndex) return false
        var next = currentCharIndex + 1
        while (
            next < characters.size &&
            !characters[next].getPhases().contains(currentGlobalPhase)
        ) {
            next++
        }
        return next >= characters.size
    }

    private fun advancePhase() {
        if (characters.isEmpty()) {
            isAllComplete = true
            return
        }
        if (practiceCharactersIndividually) {
            val phasesForCurrentCharacter = currentChar?.getPhases().orEmpty()
            val currentPhasePosition = phasesForCurrentCharacter.indexOf(currentGlobalPhase)
            if (currentPhasePosition >= 0 && currentPhasePosition < phasesForCurrentCharacter.lastIndex) {
                val nextPhase = phasesForCurrentCharacter[currentPhasePosition + 1]
                currentGlobalPhaseIndex = allPossiblePhases.indexOf(nextPhase)
            } else {
                currentCharIndex++
                if (currentCharIndex >= characters.size) {
                    isAllComplete = true
                    resetStrokeState()
                    return
                }
                val firstPhase = characters[currentCharIndex].getPhases().first()
                currentGlobalPhaseIndex = allPossiblePhases.indexOf(firstPhase)
            }
            resetStrokeState()
            scheduleAssetLoad(currentCharIndex)
            return
        }
        var nextIndex = currentCharIndex + 1
        while (
            nextIndex < characters.size &&
            !characters[nextIndex].getPhases().contains(currentGlobalPhase)
        ) {
            nextIndex++
        }
        if (nextIndex < characters.size) {
            currentCharIndex = nextIndex
        } else {
            if (currentGlobalPhaseIndex < allPossiblePhases.lastIndex) {
                currentGlobalPhaseIndex++
                currentCharIndex = 0
                while (
                    currentCharIndex < characters.size &&
                    !characters[currentCharIndex].getPhases().contains(currentGlobalPhase)
                ) {
                    currentCharIndex++
                }
                if (currentCharIndex >= characters.size) {
                    advancePhase()
                    return
                }
            } else {
                isAllComplete = true
                currentChar?.let {
                    ProgressStore.incrementSuccess(it.character)
                }
                return
            }
        }
        resetStrokeState()
        scheduleAssetLoad(currentCharIndex)
    }

    fun selectComponent(
        optionId: Int,
        component: String
    ): Boolean {
        val cp = currentChar ?: return false
        val expectedIndex = selectedComponents.size
        if (expectedIndex >= cp.orderedComponents.size) return false
        if (usedOptionIds.contains(optionId)) return false
        return if (component == cp.orderedComponents[expectedIndex].char.toString()) {
            selectedComponents.add(component)
            usedOptionIds.add(optionId)
            if (selectedComponents.size == cp.orderedComponents.size) {
                onPhaseTaskComplete()
            }
            true
        } else {
            totalErrors++
            false
        }
    }

    fun addPointToCurrentStroke(point: Offset) {
        currentStrokePoints.add(point)
    }

    fun finishStroke(canvasSize: Float) {
        val cp = currentChar ?: return
        // Assets still loading: drop the gesture instead of falsely completing
        // the phase (an empty median list otherwise means "nothing to draw").
        if (!cp.assetsLoaded) {
            currentStrokePoints.clear()
            return
        }
        if (cp.medians.isEmpty()) {
            currentStrokePoints.clear()
            onPhaseTaskComplete()
            return
        }
        if (currentStrokeIndex >= cp.medians.size) return
        val scale = canvasSize / HanziTransform.GRID_WIDTH
        val expected = cp.medians[currentStrokeIndex].map {
            HanziTransform.toCanvas(it, scale)
        }
        val isCorrect = StrokeEvaluator.evaluateStroke(
            currentStrokePoints.toList(),
            expected,
            canvasSize
        )
        if (isCorrect) {
            completedStrokes.add(currentStrokePoints.toList())
            currentStrokePoints.clear()
            currentStrokeIndex++
            if (currentStrokeIndex >= cp.medians.size) {
                onPhaseTaskComplete()
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
        selectedComponents.clear()
        usedOptionIds.clear()
    }

    /** PracticeScreen creates this view model with remember(), so a ViewModelStore
     *  never calls onCleared(). The screen MUST call dispose() when it leaves
     *  composition; otherwise abandoned sessions keep loading forever and make
     *  the entire app (including List Contents) slow and unstable. */
    fun dispose() {
        loadJob?.cancel()
        assetJob?.cancel()
        viewModelScope.cancel()
    }

    override fun onCleared() {
        dispose()
        super.onCleared()
    }

    private companion object {
        const val ASSET_LOOKAHEAD = 3
    }
}
