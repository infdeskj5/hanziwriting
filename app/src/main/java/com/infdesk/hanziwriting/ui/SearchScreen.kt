package com.infdesk.hanziwriting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.utils.PinyinUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<HanziCharacter>,
    onResultsChange: (List<HanziCharacter>) -> Unit,
    completedSearchQuery: String,
    onSearchCompleted: (String) -> Unit,
    resultsListState: LazyListState,
    onWordClick: (String) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val blankAreaInteraction = remember { MutableInteractionSource() }

    var itemToAdd by remember { mutableStateOf<String?>(null) }
    var displayWords by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var hskLevelsByWord by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    // Keep the completed-query marker in MainScreen, not here. SearchScreen is
    // temporarily removed when Word Details opens; lifted state prevents a refetch.
    val isSearching = searchQuery.isNotBlank() && completedSearchQuery != searchQuery

    BackHandler(enabled = searchQuery.isNotBlank() || searchResults.isNotEmpty()) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSearchQueryChange("")
        onResultsChange(emptyList())
        onSearchCompleted("")
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            onResultsChange(emptyList())
            onSearchCompleted("")
            displayWords = emptyMap()
            hskLevelsByWord = emptyMap()
            return@LaunchedEffect
        }

        // The result rows are lifted to MainScreen and survive Word Details, but
        // this composable's display mapping is recreated on return. Restore only
        // the compact Traditional -> Simplified mapping without rerunning search.
        if (completedSearchQuery == searchQuery) {
            displayWords = dao.getSimplifiedForms(searchQuery)
                .filter { it != searchQuery }
                .associateWith { searchQuery }
            return@LaunchedEffect
        }

        // Keep the previous completed result set in memory while this query is
        // pending. The Searching state hides it; retaining it allows an immediate
        // return to the previously completed query without another database read.
        delay(250)
        val queryBeingLoaded = searchQuery
        try {
            // Search both scripts. CC-CEDICT is stored by Simplified form, so a
            // Traditional query is first mapped to its Simplified equivalent(s).
            val convertedSimplifiedForms = dao.getSimplifiedForms(queryBeingLoaded)
                .filter { it != queryBeingLoaded }
            val queryForms = (listOf(queryBeingLoaded) + convertedSimplifiedForms).distinct()
            val exactMatches = queryForms.flatMap { dao.searchHanziExact(it) }
            val composingMatches = queryForms
                .filter { it.length > 1 }
                .flatMap { form ->
                    form.toCharArray().distinct().flatMap { dao.searchHanziExact(it.toString()) }
                }

            val results = (exactMatches + composingMatches)
                .distinctBy { it.character }
                .sortedBy { it.character != queryBeingLoaded && it.character !in convertedSimplifiedForms }

            displayWords = convertedSimplifiedForms.associateWith { queryBeingLoaded }
            onResultsChange(results)
            onSearchCompleted(queryBeingLoaded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            displayWords = emptyMap()
            onResultsChange(emptyList())
            onSearchCompleted(queryBeingLoaded)
        }
    }

    LaunchedEffect(searchResults) {
        if (searchResults.isEmpty()) {
            hskLevelsByWord = emptyMap()
        } else {
            runCatching {
                dao.getHskLevelsForWords(searchResults.map { it.character }.distinct())
            }.onSuccess { tags ->
                val levelsByForm = mutableMapOf<String, MutableSet<String>>()
                tags.forEach { tag ->
                    levelsByForm.getOrPut(tag.simplified) { mutableSetOf() }.add(tag.level)
                    if (tag.traditional.isNotBlank()) {
                        levelsByForm.getOrPut(tag.traditional) { mutableSetOf() }.add(tag.level)
                    }
                }
                hskLevelsByWord = levelsByForm.mapValues { (_, levels) -> levels.toList() }
            }
        }
    }

    // This is the same bottom-first presentation used by the working List Contents
    // screen: reverse the visual copy and bottom-align a short result set. In
    // particular, a single-character result is anchored at the bottom, not the top.
    val visualResults = remember(searchResults) { searchResults.asReversed() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Explore Hanzi", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Search by character, pinyin, or meaning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )

        if (!isSearching && searchResults.isNotEmpty()) {
            Text(
                "${searchResults.size} results",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Clicking unused space clears the text-field focus/cursor. Child cards and
        // carousel controls still receive their own clicks normally.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = blankAreaInteraction,
                    indication = null
                ) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
        ) {
            when {
                isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Searching…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                searchQuery.isBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        StrokeRulesCarousel(modifier = Modifier.fillMaxWidth())
                    }
                }

                searchResults.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No matching characters found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = resultsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            10.dp,
                            alignment = Alignment.Bottom
                        )
                    ) {
                        items(visualResults, key = { it.character }) { character ->
                            val displayWord = displayWords[character.character] ?: character.character
                            HanziResultItem(
                                character = character,
                                displayWord = displayWord,
                                hskLevels = hskLevelsByWord[character.character].orEmpty(),
                                onWordClick = { onWordClick(displayWord) },
                                onAddToListClick = { itemToAdd = displayWord }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // A stationary placeholder avoids the Material floating-label position
        // animation that appeared stuttery on the user's device.
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Hanzi, Pinyin or English") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            )
        )
    }

    itemToAdd?.let { item ->
        AddToListDialog(item = item, onDismiss = { itemToAdd = null })
    }
}

@Composable
fun HanziResultItem(
    character: HanziCharacter,
    onWordClick: () -> Unit,
    onAddToListClick: () -> Unit,
    displayWord: String = character.character,
    hskLevels: List<String> = emptyList()
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onWordClick() },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayWord,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = PinyinUtils.convertToToneMarks(character.pinyin),
                        style = MaterialTheme.typography.titleMedium
                    )
                    HskLevelChips(hskLevels, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        text = character.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    if (character.radical.isNotBlank()) {
                        Text(
                            text = "Radical: ${character.radical}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onAddToListClick) { Text("Add to List") }
                Spacer(modifier = Modifier.width(6.dp))
                Button(onClick = onWordClick, shape = MaterialTheme.shapes.medium) {
                    Text("View Details")
                }
            }
        }
    }
}
