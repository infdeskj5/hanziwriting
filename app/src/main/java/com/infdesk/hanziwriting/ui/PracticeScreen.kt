package com.infdesk.hanziwriting.ui

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

@Composable
fun PracticeScreen(
    wordToPractice: String? = null,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { PracticeViewModel(AppDatabase.getDatabase(context)) }

    LaunchedEffect(wordToPractice) {
        if (wordToPractice != null) {
            viewModel.loadCharacters(wordToPractice)
        }
    }

    val currentChar = viewModel.currentChar
    val phase = viewModel.currentPhase

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top Bar
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.Close, contentDescription = "Exit practice")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Phase ${viewModel.globalPhaseIndex + 1}/5 - Char ${viewModel.currentCharIndex + 1}/${viewModel.characters.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        if (currentChar == null || currentChar.charData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        val data = currentChar.charData!!

        // 1. TOP BLOCK (Fixed height 120.dp)
        Box(
            modifier = Modifier.height(120.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (phase) {
                PracticePhase.ANIMATION -> {
                    AnimationCanvas(
                        medians = currentChar.medians,
                        modifier = Modifier.size(100.dp),
                        autoPlay = true,
                        onComplete = { viewModel.onPhaseComplete() } // Auto advance when animation finishes
                    )
                }
                PracticePhase.TRACE_WITH_TEMPLATE -> {
                    Text(data.character, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                }
                PracticePhase.STRUCTURE_QUIZ -> {
                    StructureCanvas(
                        decomposition = data.decomposition,
                        activeIndex = currentChar.selectedComponents.size,
                        filledComponents = currentChar.selectedComponents,
                        modifier = Modifier.size(100.dp)
                    )
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

        // 2. DEFINITION BLOCK (Scrollable, max height 80.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 80.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(PinyinUtils.convertToToneMarks(data.pinyin), style = MaterialTheme.typography.titleSmall)
                Text(data.definition, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. CANVAS (Takes all remaining space!)
        val showTemplate = phase == PracticePhase.TRACE_WITH_TEMPLATE || phase == PracticePhase.TRACE_BLIND_TEMPLATE
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            HanziCanvas(
                viewModel = viewModel,
                showTemplate = showTemplate,
                modifier = Modifier.size(300.dp) // Fixed size for drawing area
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. BOTTOM CONTROLS
        if (phase == PracticePhase.STRUCTURE_QUIZ) {
            // Radical options
            val idsSymbols = setOf('⿰', '⿱', '⿲', '⿳', '⿴', '⿵', '⿶', '⿷', '⿸', '⿹', '⿺', '⿻', '？', ' ')
            val correctComponents = data.decomposition.filter { it !in idsSymbols }.map { it.toString() }.distinct()
            val distractors = listOf("氵", "木", "口", "扌", "忄", "艹", "辶", "阝", "刂", "日", "月", "女", "心")
            val options = remember(correctComponents) {
                (correctComponents + distractors.shuffled().take(4)).shuffled()
            }
            
            Text("Select components in order:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { comp ->
                    Button(
                        onClick = { viewModel.selectComponent(comp) },
                        enabled = !currentChar.selectedComponents.contains(comp)
                    ) {
                        Text(comp, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            // Stroke count and errors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Stroke: ${minOf(viewModel.currentStrokeIndex + 1, currentChar.medians.size)}/${currentChar.medians.size}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Errors: ${viewModel.totalErrors}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                if (viewModel.isAllComplete) {
                    Button(onClick = onBackClick) {
                        Text("Finish!")
                    }
                }
            }
        }
    }
}
