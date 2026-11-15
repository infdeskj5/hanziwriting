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
import kotlinx.coroutines.withTimeoutOrNull

private fun isCjkSearchCharacter(char: Char): Boolean =
    char in '㐀'..'䶿' || char in '一'..'鿿' ||
        char in '豈'..'﫿'

private data class SearchOutcome(
    val results: List<HanziCharacter>,
    val displayWords: Map<String, String>
)

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

    var searchInProgress by remember(searchQuery) {
        mutableStateOf(searchQuery.isNotBlank() && completedSearchQuery != searchQuery)
    }
    var searchErrorMessage by remember(searchQuery) { mutableStateOf<String?>(null) }
    var retryCount by remember(searchQuery) { mutableStateOf(0) }
    val latestSearchQuery by rememberUpdatedState(searchQuery)

    BackHandler(enabled = searchQuery.isNotBlank() || searchResults.isNotEmpty()) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSearchQueryChange("")
        onResultsChange(emptyList())
        onSearchCompleted("")
    }

    LaunchedEffect(searchQuery, retryCount) {
        val requestedQuery = searchQuery
        if (requestedQuery.isBlank()) {
            searchInProgress = false
            searchErrorMessage = null
            onResultsChange(emptyList())
            onSearchCompleted("")
            displayWords = emptyMap()
            hskLevelsByWord = emptyMap()
            return@LaunchedEffect
        }

        if (completedSearchQuery == requestedQuery && retryCount == 0) {
            searchInProgress = false
            val restoredForms: List<String> = try {
                withTimeoutOrNull(5_000L) {
                    if (requestedQuery.all(::isCjkSearchCharacter)) {
                        dao.getSimplifiedForms(requestedQuery)
                    } else {
                        emptyList()
                    }
                } ?: emptyList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            displayWords = restoredForms
                .filter { it != requestedQuery }
                .associateWith { requestedQuery }
            return@LaunchedEffect
        }

        searchInProgress = true
        searchErrorMessage = null
        var finished = false
        var markQueryCompleted = false
        try {
            delay(140L)
            val query = requestedQuery.trim()
            val outcome = withTimeoutOrNull(20_000L) {
                if (query.all(::isCjkSearchCharacter)) {
                    val convertedForms: List<String> = try {
                        dao.getSimplifiedForms(query).filter { it != query }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val queryForms = (listOf(query) + convertedForms).distinct()
                    val matches = LinkedHashMap<String, HanziCharacter>()
                    for (form in queryForms) {
                        dao.getCharacterExact(form)?.let { matches[it.character] = it }
                        if (form.length > 1) {
                            form.toCharArray().distinct().forEach { component ->
                                dao.getCharacterExact(component.toString())?.let {
                                    matches[it.character] = it
                                }
                            }
                        }
                    }
                    if (matches.isEmpty()) {
                        for (form in queryForms) {
                            dao.searchHanziForSearch(form).forEach {
                                matches[it.character] = it
                            }
                        }
                    }
                    val ordered = matches.values
                        .sortedBy { it.character !in queryForms }
                    SearchOutcome(
                        results = ordered,
                        displayWords = convertedForms.associateWith { query }
                    )
                } else {
                    SearchOutcome(
                        results = dao.searchHanziForSearch(query),
                        displayWords = emptyMap()
                    )
                }
            }

            if (outcome == null) {
                searchErrorMessage = "The search took too long. Please try again."
                onResultsChange(emptyList())
                displayWords = emptyMap()
                android.util.Log.w("HanziSearch", "Search timed out for: $query")
            } else {
                displayWords = outcome.displayWords
                onResultsChange(outcome.results)
                markQueryCompleted = true
            }
            finished = true
            // Publish completion BEFORE scrolling so the LazyColumn exists when the
            // scroll runs (avoids the old "Searching…" deadlock).
            searchInProgress = false
            onSearchCompleted(if (markQueryCompleted) requestedQuery else "")
            if (outcome != null && outcome.results.isNotEmpty()) {
                // The list is no longer reverseLayout: the visual order is the
                // reversed result list, so the bottom edge is the LAST visual index.
                // scrollToItem clamps to max scroll, which puts the priority result
                // (original index 0) flush against the bottom for both one-card and
                // full-page result sets. Bounded so a scroll can never stall search.
                val bottomIndex = outcome.results.size - 1
                withTimeoutOrNull(1_000L) { resultsListState.scrollToItem(bottomIndex, 0) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("HanziSearch", "Search failed for: $requestedQuery", error)
            searchErrorMessage = "Couldn't complete the search. Please try again."
            displayWords = emptyMap()
            onResultsChange(emptyList())
            markQueryCompleted = false
            finished = true
        } finally {
            if (finished && latestSearchQuery == requestedQuery) {
                searchInProgress = false
                onSearchCompleted(if (markQueryCompleted) requestedQuery else "")
            }
        }
    }

    LaunchedEffect(searchResults) {
        if (searchResults.isEmpty()) {
            hskLevelsByWord = emptyMap()
        } else {
            try {
                val tags = dao.getHskLevelsForWords(searchResults.map { it.character }.distinct())
                val levelsByForm = mutableMapOf<String, MutableSet<String>>()
                tags.forEach { tag ->
                    levelsByForm.getOrPut(tag.simplified) { mutableSetOf() }.add(tag.level)
                    if (tag.traditional.isNotBlank()) {
                        levelsByForm.getOrPut(tag.traditional) { mutableSetOf() }.add(tag.level)
                    }
                }
                hskLevelsByWord = levelsByForm.mapValues { (_, levels) -> levels.toList() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("HanziSearch", "Unable to load HSK labels", error)
                hskLevelsByWord = emptyMap()
            }
        }
    }

    val isSearching = searchInProgress
    // Normal (non-reversed) LazyColumn + reversed data + bottom alignment. This is
    // the same pattern ListsScreen uses: on Compose 1.6.x a reverseLayout list only
    // bottom-anchors content that fills the viewport (a single result card was
    // placed at the top), and its mirrored placement path is also the slow one for
    // long lists. Alignment.Bottom guarantees the block sits at the bottom edge
    // when results are short; scrollToItem(lastIndex) guarantees it for long sets.
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
                searchErrorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            searchErrorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { retryCount += 1 }) {
                            Text("Try again")
                        }
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
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
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
