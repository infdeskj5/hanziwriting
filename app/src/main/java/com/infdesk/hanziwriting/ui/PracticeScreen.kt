package com.infdesk.hanziwriting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.utils.PinyinUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
    wordToPractice: String? = null,
    onBackClick: () -> Unit = {}
) {
    BackHandler(enabled = true) { onBackClick() }

    val context = LocalContext.current
    val viewModel = remember { PracticeViewModel(AppDatabase.getDatabase(context)) }

    LaunchedEffect(wordToPractice) {
        if (wordToPractice != null) viewModel.loadCharacters(wordToPractice)
    }

    val currentChar = viewModel.currentChar
    val phase = viewModel.currentGlobalPhase

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) { Icon(Icons.Default.Close, contentDescription = "Exit practice") }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Phase ${viewModel.currentGlobalPhaseIndex + 1}/5 - Char ${viewModel.currentCharIndex + 1}/${viewModel.characters.size}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        if (currentChar == null || currentChar.charData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        val data = currentChar.charData!!

        // TOP BLOCK
        Box(modifier = Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (phase) {
                PracticePhase.ANIMATION -> {
                    // Looping animation plays WHILE you practice
                    AnimationCanvas(
                        medians = currentChar.medians,
                        modifier = Modifier.size(100.dp),
                        autoPlay = true,
                        loop = true,
                        onComplete = {}
                    )
                }
                PracticePhase.TRACE_WITH_TEMPLATE -> {
                    Text(data.character, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                }
                PracticePhase.STRUCTURE_QUIZ -> {
                    Text("Select components in writing order", style = MaterialTheme.typography.titleMedium)
                }
                PracticePhase.TRACE_BLIND_TEMPLATE -> {
                    Text("Draw with template", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PracticePhase.MEMORY_DRAW -> {
                    Text("Draw from memory", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // DEFINITION (scrollable, capped height)
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 80.dp).verticalScroll(rememberScrollState())) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(PinyinUtils.convertToToneMarks(data.pinyin), style = MaterialTheme.typography.titleSmall)
                Text(data.definition, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // MIDDLE AREA: structure view (quiz) OR drawing canvas
        if (phase == PracticePhase.STRUCTURE_QUIZ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                StructureView(
                    root = currentChar.idsRoot,
                    leafRank = currentChar.leafRank,
                    selectedCount = viewModel.selectedComponents.size,
                    modifier = Modifier.size(220.dp)
                )
            }
        } else {
            val showTemplate = phase == PracticePhase.ANIMATION ||
                               phase == PracticePhase.TRACE_WITH_TEMPLATE ||
                               phase == PracticePhase.TRACE_BLIND_TEMPLATE
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                HanziCanvas(viewModel = viewModel, showTemplate = showTemplate, modifier = Modifier.size(300.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // BOTTOM CONTROLS
        if (phase == PracticePhase.STRUCTURE_QUIZ) {
            val correctComponents = currentChar.orderedComponents.map { it.char.toString() }
            val distractors = listOf("氵", "木", "口", "扌", "忄", "艹", "辶", "阝", "刂", "日", "月", "女", "心")
            val options = remember(correctComponents) { (correctComponents + distractors.shuffled().take(4)).shuffled() }

            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { comp ->
                    Button(
                        onClick = { viewModel.selectComponent(comp) },
                        enabled = !viewModel.selectedComponents.contains(comp)
                    ) { Text(comp, style = MaterialTheme.typography.titleMedium) }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Stroke: ${minOf(viewModel.currentStrokeIndex + 1, currentChar.medians.size)}/${currentChar.medians.size}", style = MaterialTheme.typography.bodyLarge)
                    Text("Errors: ${viewModel.totalErrors}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
                if (viewModel.isAllComplete) {
                    Button(onClick = onBackClick) { Text("Finish!") }
                }
            }
        }
    }
}
