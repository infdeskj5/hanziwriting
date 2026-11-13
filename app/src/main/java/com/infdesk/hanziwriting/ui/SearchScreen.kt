package com.infdesk.hanziwriting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.utils.PinyinUtils
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<HanziCharacter>,
    onResultsChange: (List<HanziCharacter>) -> Unit,
    onWordClick: (String) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val resultsState = rememberLazyListState()
    val emptyAreaInteraction = remember { MutableInteractionSource() }

    var itemToAdd by remember { mutableStateOf<String?>(null) }

    // Back from a populated search returns to the clean Search home instead of
    // allowing the activity to close. The IME still gets its first Back event.
    BackHandler(enabled = searchQuery.isNotBlank() || searchResults.isNotEmpty()) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onSearchQueryChange("")
        onResultsChange(emptyList())
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            onResultsChange(emptyList())
        } else {
            delay(280)
            val exactMatches = dao.searchHanziExact(searchQuery)
            val composingMatches = if (searchQuery.length > 1 && exactMatches.isNotEmpty()) {
                searchQuery.toCharArray().distinct().map { it.toString() }
                    .flatMap { dao.searchHanziExact(it) }
            } else {
                emptyList()
            }

            onResultsChange(
                (exactMatches + composingMatches)
                    .distinctBy { it.character }
                    .sortedBy { it.character != searchQuery }
            )
        }
    }

    LaunchedEffect(searchQuery, searchResults) {
        if (searchResults.size > 1) resultsState.scrollToItem(0)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Explore Hanzi", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Search by character, pinyin, or meaning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )

        if (searchResults.isNotEmpty()) {
            Text(
                "${searchResults.size} results",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // A single result is bottom-aligned. For the empty initial screen this
        // same reachable area becomes the stroke-order learning carousel.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = emptyAreaInteraction,
                    indication = null
                ) {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                }
        ) {
            when {
                searchQuery.isBlank() && searchResults.isEmpty() -> {
                    StrokeRulesCarousel(modifier = Modifier.align(Alignment.Center))
                }

                searchResults.size == 1 -> {
                    val onlyResult = searchResults[0]
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        HanziResultItem(
                            character = onlyResult,
                            onWordClick = { onWordClick(onlyResult.character) },
                            onAddToListClick = { itemToAdd = onlyResult.character }
                        )
                    }
                }

                searchResults.isNotEmpty() -> {
                    LazyColumn(
                        state = resultsState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(searchResults, key = { it.character }) { character ->
                            HanziResultItem(
                                character = character,
                                onWordClick = { onWordClick(character.character) },
                                onAddToListClick = { itemToAdd = character.character }
                            )
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No matching characters found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // No floating label: a fixed placeholder avoids the animated label
        // translation that was stuttering when the Realme's keyboard appeared.
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search Hanzi, Pinyin, or English") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
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
    onAddToListClick: () -> Unit
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
                    text = character.character,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = PinyinUtils.convertToToneMarks(character.pinyin),
                        style = MaterialTheme.typography.titleMedium
                    )
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
