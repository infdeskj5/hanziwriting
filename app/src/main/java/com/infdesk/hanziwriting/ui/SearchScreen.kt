package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.data.ListItem
import com.infdesk.hanziwriting.data.PracticeList
import com.infdesk.hanziwriting.utils.PinyinUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    var showAddToListDialog by remember { mutableStateOf(false) }
    var characterToAdd by remember { mutableStateOf("") }
    var availableLists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }

    LaunchedEffect(showAddToListDialog) {
        if (showAddToListDialog) availableLists = dao.getAllLists()
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(300)
            val exactMatches = dao.searchHanziExact(searchQuery)
            val composingMatches = if (searchQuery.length > 1 && exactMatches.isNotEmpty()) {
                val chars = searchQuery.toCharArray().distinct().map { it.toString() }
                chars.flatMap { dao.searchHanziExact(it) }
            } else {
                emptyList()
            }
            val results = (exactMatches + composingMatches)
                .distinctBy { it.character }
                .sortedBy { it.character != searchQuery }
            onResultsChange(results)
        } else {
            onResultsChange(emptyList())
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(searchResults) { char ->
                HanziResultItem(
                    character = char,
                    onWordClick = { onWordClick(char.character) },
                    onAddToListClick = {
                        characterToAdd = char.character
                        showAddToListDialog = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search Hanzi, Pinyin, or English") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
    }

    if (showAddToListDialog) {
        AlertDialog(
            onDismissRequest = { showAddToListDialog = false },
            title = { Text("Add '$characterToAdd' to List") },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(availableLists) { list ->
                            TextButton(onClick = { 
                                coroutineScope.launch {
                                    dao.addCharacterToList(ListItem(listId = list.listId, character = characterToAdd))
                                    showAddToListDialog = false
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(list.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddToListDialog = false }) { Text("Cancel") } }
        )
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = character.character, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = PinyinUtils.convertToToneMarks(character.pinyin), style = MaterialTheme.typography.titleMedium)
                    Text(text = character.definition, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    if (character.radical.isNotBlank()) {
                        Text(text = "Radical: ${character.radical}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAddToListClick) { Text("Add to List") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onWordClick) { Text("View Details") } 
            }
        }
    }
}
