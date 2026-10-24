package com.infdesk.hanziwriting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.data.HanziGraphics
import com.infdesk.hanziwriting.utils.PinyinUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordPage(
    word: String,
    onBackClick: () -> Unit,
    onComponentClick: (String) -> Unit,
    onPracticeClick: (String) -> Unit
) {
    BackHandler(enabled = true) { onBackClick() }

    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }

    var charData by remember { mutableStateOf<HanziCharacter?>(null) }
    var graphicsData by remember { mutableStateOf<HanziGraphics?>(null) }
    var components by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(word) {
        charData = dao.getCharacterExact(word)
        graphicsData = dao.getGraphics(word)

        val idsSymbols = setOf('⿰', '', '', '⿳', '⿴', '', '', '⿷', '⿸', '⿹', '⿺', '', '？', ' ')
        components = charData?.decomposition
            ?.filter { it !in idsSymbols }
            ?.map { it.toString() }
            ?.distinct() ?: emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Word Details", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (charData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val data = charData!!

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(data.character, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text(PinyinUtils.convertToToneMarks(data.pinyin), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))

                // BOTH dictionary definitions
                if (data.definition.isNotBlank()) {
                    Text(data.definition, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                if (data.mmaDefinition.isNotBlank() && data.mmaDefinition != data.definition) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        data.mmaDefinition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (components.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Components", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        components.forEach { comp ->
                            AssistChip(
                                onClick = { onComponentClick(comp) },
                                label = { Text(comp, style = MaterialTheme.typography.titleLarge) }
                            )
                        }
                    }
                }

                if (word.length > 1 || graphicsData != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Stroke Order", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(word.toList().map { it.toString() }) { char ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(200.dp)
                            ) {
                                Text(
                                    char,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                val miniVm = remember(char) { PracticeViewModel(AppDatabase.getDatabase(context)) }
                                LaunchedEffect(char) { miniVm.loadCharacters(char) }
                                val charProgress = miniVm.characters.firstOrNull()

                                Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                                    if (charProgress != null && charProgress.medians.isNotEmpty()) {
                                        AnimationCanvas(
                                            medians = charProgress.medians,
                                            strokes = charProgress.strokes,
                                            modifier = Modifier.size(170.dp),
                                            autoPlay = true,
                                            loop = true,
                                            onComplete = {}
                                        )
                                    } else {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onPracticeClick(word) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Practice", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
