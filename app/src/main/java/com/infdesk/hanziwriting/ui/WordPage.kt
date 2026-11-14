package com.infdesk.hanziwriting.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    var components by remember { mutableStateOf<List<String>>(emptyList()) }
    var alternateForms by remember { mutableStateOf<List<String>>(emptyList()) }
    var alternateSectionTitle by remember { mutableStateOf("Traditional Chinese") }
    var hskLevels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddToListDialog by remember { mutableStateOf(false) }

    LaunchedEffect(word) {
        isLoading = true
        charData = null
        components = emptyList()
        alternateForms = emptyList()
        hskLevels = emptyList()

        val forms = dao.getWordForms(word)
        val isTraditionalInput =
            forms.none { it.simplified == word } && forms.any { it.traditional == word && it.simplified != word }

        val canonicalWord = if (isTraditionalInput) {
            forms.firstOrNull { it.traditional == word && it.simplified != word }?.simplified ?: word
        } else {
            word
        }

        alternateForms = if (isTraditionalInput) {
            forms.filter { it.traditional == word }.map { it.simplified }.distinct().filter { it != word }
        } else {
            forms.filter { it.simplified == word }.map { it.traditional }.distinct().filter { it != word }
        }
        alternateSectionTitle = if (isTraditionalInput) "Simplified Chinese" else "Traditional Chinese"

        // CC-CEDICT is keyed by Simplified form. Traditional input is resolved via
        // the prebuilt form-pair table, but the word the user opened remains visible.
        val loadedCharacter = dao.getCharacterExact(canonicalWord) ?: dao.getCharacterExact(word)
        charData = loadedCharacter

        val idsSymbols = setOf(
            '⿰', '⿱', '⿲', '⿳', '⿴', '⿵', '⿶', '⿷',
            '⿸', '⿹', '⿺', '⿻', '？', ' '
        )
        components = loadedCharacter?.decomposition
            ?.filter { it !in idsSymbols }
            ?.map { it.toString() }
            ?.distinct()
            ?: emptyList()

        hskLevels = dao.getHskLevelsForWord(canonicalWord).ifEmpty {
            dao.getHskLevelsForWord(word)
        }
        isLoading = false
    }

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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Word Details", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> Spacer(modifier = Modifier.weight(1f))
            charData == null -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No dictionary entry was found for $word.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                val data = charData!!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                word,
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                PinyinUtils.convertToToneMarks(data.pinyin),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            HskLevelChips(hskLevels, modifier = Modifier.padding(top = 8.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                data.definition,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (components.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Components",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            components.forEach { component ->
                                AssistChip(
                                    onClick = { onComponentClick(component) },
                                    label = { Text(component, style = MaterialTheme.typography.titleLarge) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Stroke Order",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StrokeAnimationStrip(text = word, context = context)

                    if (alternateForms.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            alternateSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        alternateForms.forEach { alternateWord ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(
                                        alternateWord,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StrokeAnimationStrip(text = alternateWord, context = context)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showAddToListDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add to List", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onPracticeClick(word) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Practice", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showAddToListDialog) {
        AddToListDialog(item = word, onDismiss = { showAddToListDialog = false })
    }
}

@Composable
private fun StrokeAnimationStrip(text: String, context: Context) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Do not use the character itself as the key: words may repeat a glyph.
        items(text.toList().map { it.toString() }) { character ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(character, style = MaterialTheme.typography.titleMedium)

                val miniViewModel = remember(character) {
                    PracticeViewModel(AppDatabase.getDatabase(context))
                }
                LaunchedEffect(character) {
                    miniViewModel.loadCharacters(character)
                }
                val characterProgress = miniViewModel.characters.firstOrNull()

                if (characterProgress != null && characterProgress.medians.isNotEmpty()) {
                    AnimationCanvas(
                        medians = characterProgress.medians,
                        strokes = characterProgress.strokes,
                        modifier = Modifier.size(100.dp),
                        autoPlay = true,
                        loop = true,
                        showGuide = true,
                        useSvgFill = true,
                        frameDelayMs = 16,
                        onComplete = {}
                    )
                } else {
                    Spacer(modifier = Modifier.size(100.dp))
                }
            }
        }
    }
}
