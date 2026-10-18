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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onPracticeClick: (String) -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<HanziCharacter>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    var showAddToListDialog by remember { mutableStateOf(false) }
    var characterToAdd by remember { mutableStateOf("") }
    var availableLists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }

    LaunchedEffect(showAddToListDialog) {
        if (showAddToListDialog) availableLists = dao.getAllLists()
    }

    val performSearch = suspend {
        isSearching = true
        val exactMatches = dao.searchHanziExact(searchQuery)
        
        val composingMatches = if (searchQuery.length > 1 && exactMatches.isNotEmpty()) {
            val chars = searchQuery.toCharArray().distinct().map { it.toString() }
            chars.flatMap { dao.searchHanziExact(it) }
        } else {
            emptyList()
        }
        
        searchResults = (exactMatches + composingMatches)
            .distinctBy { it.character }
            .sortedBy { it.character != searchQuery }
            
        isSearching = false
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (searchResults.isEmpty() && !isSearching) {
            Spacer(modifier = Modifier.weight(1f))
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Hanzi, Pinyin, or English") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { coroutineScope.launch { performSearch() } }
            )
        )

        if (searchResults.isEmpty() && !isSearching) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { char ->
                    HanziResultItem(
                        character = char,
                        onPracticeClick = { onPracticeClick(char.character) },
                        onAddToListClick = {
                            characterToAdd = char.character
                            showAddToListDialog = true
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { coroutineScope.launch { performSearch() } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSearching && searchQuery.isNotBlank(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(if (isSearching) "Searching..." else "Search")
        }
    }

    if (showAddToListDialog) {
        AddToListDialog(
            character = characterToAdd,
            lists = availableLists,
            onDismiss = { showAddToListDialog = false },
            onAddToExisting = { listId ->
                coroutineScope.launch {
                    dao.addCharacterToList(ListItem(listId = listId, character = characterToAdd))
                    showAddToListDialog = false
                }
            },
            onCreateNew = { newName ->
                coroutineScope.launch {
                    val newListId = dao.insertList(PracticeList(name = newName))
                    dao.addCharacterToList(ListItem(listId = newListId.toInt(), character = characterToAdd))
                    showAddToListDialog = false
                }
            }
        )
    }
}

@Composable
fun HanziResultItem(
    character: HanziCharacter,
    onPracticeClick: () -> Unit,
    onAddToListClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPracticeClick() },
        shape = MaterialTheme.shapes.medium,
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
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAddToListClick) { Text("Add to List") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onPracticeClick) { Text("Practice") }
            }
        }
    }
}

@Composable
fun AddToListDialog(
    character: String,
    lists: List<PracticeList>,
    onDismiss: () -> Unit,
    onAddToExisting: (Int) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var newListName by remember { mutableStateOf("") }
    var showNewListInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add '$character' to List") },
        text = {
            Column {
                if (showNewListInput) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        label = { Text("New List Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { if (newListName.isNotBlank()) onCreateNew(newListName) }) {
                            Text("Create & Add")
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(lists) { list ->
                            TextButton(onClick = { onAddToExisting(list.listId) }, modifier = Modifier.fillMaxWidth()) {
                                Text(list.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            }
                        }
                        item {
                            TextButton(onClick = { showNewListInput = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("+ Create New List", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
