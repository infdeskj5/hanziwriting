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

private data class QuizOption(
    val id: Int,
    val label: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
    wordToPractice: String? = null,
    onBackClick: () -> Unit = {}
) {
    BackHandler(enabled = true) { onBackClick() }

    val context = LocalContext.current
    val viewModel = remember {
        PracticeViewModel(AppDatabase.getDatabase(context))
    }

    LaunchedEffect(wordToPractice) {
        if (wordToPractice != null) {
            viewModel.loadCharacters(wordToPractice)
        }
    }

    val currentChar = viewModel.currentChar
    val phase = viewModel.currentGlobalPhase

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Exit practice"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "Phase ${viewModel.currentGlobalPhaseIndex + 1}/5 - " +
                    "Char ${viewModel.currentCharIndex + 1}/${viewModel.characters.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        if (currentChar == null || currentChar.charData == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        val data = currentChar.charData!!

        Box(
            modifier = Modifier
                .height(120.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (phase) {
                PracticePhase.ANIMATION -> {
                    AnimationCanvas(
                        medians = currentChar.medians,
                        strokes = currentChar.strokes,
                        modifier = Modifier.size(100.dp),
                        autoPlay = true,
                        loop = true,
                        showGuide = true,
                        useSvgFill = true,
                        frameDelayMs = 16,
                        onComplete = {}
                    )
                }

                PracticePhase.TRACE_WITH_TEMPLATE -> {
                    Text(
                        data.character,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                PracticePhase.STRUCTURE_QUIZ -> {
                    Text(
                        "Select components in writing order",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                PracticePhase.TRACE_BLIND_TEMPLATE -> {
                    Text(
                        "Draw with template",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PracticePhase.MEMORY_DRAW -> {
                    Text(
                        "Draw from memory",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 80.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    PinyinUtils.convertToToneMarks(data.pinyin),
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    data.definition,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (phase == PracticePhase.STRUCTURE_QUIZ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HanziCanvas(
                    viewModel = viewModel,
                    showTemplate = showTemplate,
                    modifier = Modifier.size(300.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (phase == PracticePhase.STRUCTURE_QUIZ) {
            val correctLabels = currentChar.orderedComponents.map { it.char.toString() }
            val correctLabelSet = correctLabels.toSet()

            val correctOptions = currentChar.orderedComponents.mapIndexed { index, component ->
                QuizOption(
                    id = index,
                    label = component.char.toString()
                )
            }

            val distractorPool = listOf(
                "氵", "木", "口", "扌", "忄", "艹", "辶", "阝",
                "刂", "日", "月", "女", "心", "人", "大", "小"
            ).filter { it !in correctLabelSet }

            val distractorOptions = distractorPool
                .shuffled()
                .take(4)
                .mapIndexed { index, label ->
                    QuizOption(
                        id = correctOptions.size + index,
                        label = label
                    )
                }

            val options = remember(
                currentChar.character,
                currentChar.orderedComponents
            ) {
                (correctOptions + distractorOptions).shuffled()
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val used = viewModel.usedOptionIds.contains(option.id)

                    Button(
                        onClick = {
                            if (!used && !viewModel.waitingForNext) {
                                viewModel.selectComponent(
                                    option.id,
                                    option.label
                                )
                            }
                        },
                        enabled = !used && !viewModel.waitingForNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = if (used) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            disabledContentColor = if (used) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } else {
            // Keep the status panel and the Done action in one footer row.
            // This prevents the action from consuming an additional row of vertical space.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Stroke: " +
                            "${minOf(viewModel.currentStrokeIndex + 1, currentChar.medians.size)}" +
                            "/${currentChar.medians.size}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        "Errors: ${viewModel.totalErrors}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (viewModel.waitingForNext) {
                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { viewModel.proceedNext() },
                        modifier = Modifier.height(40.dp),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(
                            horizontal = 20.dp,
                            vertical = 0.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            "Done",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        if (viewModel.waitingForNext && phase == PracticePhase.STRUCTURE_QUIZ) {
            // The structure quiz has no Stroke/Errors panel, so align Done at the
            // same footer position without introducing a full-width button row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { viewModel.proceedNext() },
                    modifier = Modifier.height(40.dp),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(
                        horizontal = 20.dp,
                        vertical = 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        "Done",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        } else if (!viewModel.waitingForNext && viewModel.isAllComplete) {
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    "Finish!",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
