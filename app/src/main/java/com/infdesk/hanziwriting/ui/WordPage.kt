package com.infdesk.hanziwriting.ui

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.utils.DebugLog
import com.infdesk.hanziwriting.utils.PinyinUtils

private data class StrokeAnimationData(
    val medians: List<List<List<Double>>>,
    val strokes: List<String>
)

private fun parseStrokeAnimationData(rawJson: String): StrokeAnimationData? = runCatching {
    val root = JsonParser.parseString(rawJson).asJsonObject
    val gson = Gson()
    val mediansType = object : TypeToken<List<List<List<Double>>>>() {}.type
    val strokesType = object : TypeToken<List<String>>() {}.type
    val medians: List<List<List<Double>>> = gson.fromJson(root.get("medians"), mediansType)
        ?: emptyList()
    val strokes: List<String> = gson.fromJson(root.get("strokes"), strokesType)
        ?: emptyList()
    StrokeAnimationData(medians, strokes).takeIf { it.medians.isNotEmpty() }
}.getOrNull()

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
    val clipboard = LocalClipboardManager.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    var charData by remember { mutableStateOf<HanziCharacter?>(null) }
    var components by remember { mutableStateOf<List<String>>(emptyList()) }
    var alternateForms by remember { mutableStateOf<List<String>>(emptyList()) }
    var alternateSectionTitle by remember { mutableStateOf("Traditional Chinese") }
    var hskLevels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddToListDialog by remember { mutableStateOf(false) }

    fun copyValue(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

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
            forms.filter { it.traditional == word }
                .map { it.simplified }
                .distinct()
                .filter { it != word }
        } else {
            forms.filter { it.simplified == word }
                .map { it.traditional }
                .distinct()
                .filter { it != word }
        }
        alternateSectionTitle = if (isTraditionalInput) "Simplified Chinese" else "Traditional Chinese"
        // CC-CEDICT is keyed by Simplified form. Traditional input is resolved via
        // the prebuilt form-pair table, but the form the user opened remains visible.
        val loadedCharacter = dao.getCharacterExact(canonicalWord) ?: dao.getCharacterExact(word)
        charData = loadedCharacter
        // Ideographic Description Characters U+2FF0..U+2FFB, written as escapes so
        // copy/paste pipelines can never turn them into empty character literals.
        val idsSymbols = setOf(
            '⿰', '⿱', '⿲', '⿳', '⿴', '⿵',
            '⿶', '⿷', '⿸', '⿹', '⿺', '⿻',
            '？', ' '
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
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    item(key = "word-summary") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        word,
                                        style = MaterialTheme.typography.displayLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    IconButton(onClick = { copyValue("Word", word) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy word")
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        PinyinUtils.convertToToneMarks(data.pinyin),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    IconButton(
                                        onClick = { copyValue("Pinyin", PinyinUtils.convertToToneMarks(data.pinyin)) },
                                        enabled = data.pinyin.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy pinyin")
                                    }
                                }
                                HskLevelChips(hskLevels, modifier = Modifier.padding(top = 8.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        data.definition,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { copyValue("Definition", data.definition) },
                                        enabled = data.definition.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy definition")
                                    }
                                }
                            }
                        }
                    }
                    if (components.isNotEmpty()) {
                        item(key = "word-components") {
                            Column {
                                Text("Components", style = MaterialTheme.typography.titleMedium)
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
                        }
                    }
                    item(key = "simplified-stroke-order") {
                        Column {
                            Text("Stroke Order", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            StrokeAnimationStrip(
                                text = word,
                                context = context,
                                onCharacterClick = onComponentClick
                            )
                        }
                    }
                    if (alternateForms.isNotEmpty()) {
                        item(key = "alternate-script-heading") {
                            Text(alternateSectionTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        itemsIndexed(
                            alternateForms,
                            key = { index, alternateWord -> "alternate-$index-$alternateWord" }
                        ) { _, alternateWord ->
                            Column {
                                if (alternateForms.size > 1) {
                                    Text(
                                        alternateWord,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                // Match the regular Stroke Order strip: separate glyph
                                // tiles, composed only when this section is near the viewport.
                                StrokeAnimationStrip(
                                    text = alternateWord,
                                    context = context,
                                    onCharacterClick = onComponentClick
                                )
                            }
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
private fun StrokeAnimationStrip(
    text: String,
    context: Context,
    onCharacterClick: (String) -> Unit
) {
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val glyphs = remember(text) { text.mapIndexed { index, character -> index to character.toString() } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(glyphs, key = { _, glyph -> "${glyph.first}:${glyph.second}" }) { _, glyph ->
            StrokeAnimationTile(
                character = glyph.second,
                dao = dao,
                onClick = { onCharacterClick(glyph.second) }
            )
        }
    }
}

@Composable
private fun StrokeAnimationTile(
    character: String,
    dao: com.infdesk.hanziwriting.data.HanziDao,
    onClick: () -> Unit
) {
    var animationData by remember(character) { mutableStateOf<StrokeAnimationData?>(null) }
    var loaded by remember(character) { mutableStateOf(false) }

    LaunchedEffect(character) {
        // Measurement: this runs on Dispatchers.Main; log query vs parse cost and
        // the thread name so the report shows any main-thread Gson work.
        val t0 = SystemClock.uptimeMillis()
        val raw = dao.getGraphics(character)?.rawJson
        val tQ = SystemClock.uptimeMillis()
        animationData = raw?.let(::parseStrokeAnimationData)
        DebugLog.d(
            "WordPageParse",
            "char=$character queryMs=${tQ - t0} parseMs=${SystemClock.uptimeMillis() - tQ} " +
                "thread=${Thread.currentThread().name}"
        )
        loaded = true
    }

    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(character, style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            val data = animationData
            if (data != null) {
                AnimationCanvas(
                    medians = data.medians,
                    strokes = data.strokes,
                    modifier = Modifier.size(100.dp),
                    autoPlay = true,
                    loop = true,
                    showGuide = true,
                    useSvgFill = true,
                    frameDelayMs = 33,
                    onComplete = {}
                )
            } else if (!loaded) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}
