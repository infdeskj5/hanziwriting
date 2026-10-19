package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
    characterToPractice: String? = null,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { PracticeViewModel(AppDatabase.getDatabase(context)) }

    LaunchedEffect(characterToPractice) {
        if (characterToPractice != null) {
            viewModel.loadCharacters(characterToPractice)
        }
    }

    val data = viewModel.practiceData
    val error = viewModel.loadError

    val guideLabel = when (viewModel.guideLevel) {
        0 -> "Guide: Full"
        1 -> "Guide: Faded"
        2 -> "Guide: Hidden"
        else -> "Memory Mode"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.Close, contentDescription = "Exit practice")
            }
            Spacer(modifier = Modifier.weight(1f))
            if (viewModel.totalCharacters > 1) {
                Text(
                    "${viewModel.currentQueueIndex + 1} / ${viewModel.totalCharacters}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        when {
            error != null -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not load character", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick) { Text("Go Back") }
                    }
                }
            }
            data == null -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                // Scrollable middle section to prevent bottom cut-off
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Hide character text in memory mode (no "?" symbol)
                            Text(
                                if (viewModel.guideLevel >= 3) " " else data.character,
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                PinyinUtils.convertToToneMarks(data.pinyin),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(data.definition, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            
                            // Show all components/radicals
                            if (data.components.isNotEmpty()) {
                                Text(
                                    "Components: ${data.components.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else if (!data.radical.isNullOrBlank()) {
                                Text(
                                    "Radical: ${data.radical}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HanziCanvas(viewModel = viewModel, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Pinned bottom controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        // FIX: Cap stroke display at totalStrokes
                        Text(
                            "Stroke: ${minOf(viewModel.currentStrokeIndex + 1, data.totalStrokes)}/${data.totalStrokes}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Errors: ${viewModel.totalErrors}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(guideLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.hasPreviousCharacter) {
                            IconButton(onClick = { viewModel.previousCharacter() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Previous character")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (viewModel.isCompleted) viewModel.practiceAgain()
                                else viewModel.resetPractice()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (viewModel.isCompleted) "Again" else "Reset")
                        }
                        if (viewModel.isCompleted) {
                            Spacer(Modifier.width(8.dp))
                            if (viewModel.hasNextCharacter) {
                                Button(
                                    onClick = { viewModel.nextCharacter() },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text("Next")
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                                }
                            } else {
                                // Stay on screen, no "Done" button that exits
                                Button(
                                    onClick = { viewModel.practiceAgain() },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text("Again")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
