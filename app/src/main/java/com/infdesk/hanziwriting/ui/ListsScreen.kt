package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.PracticeList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ListsScreen(
    onPracticeListClick: (Int) -> Unit,
    onListContentsClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { FocusRequester() }

    var lists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        lists = dao.getAllLists()
        isLoading = false
    }

    LaunchedEffect(showCreateListDialog) {
        if (showCreateListDialog) {
            delay(120)
            nameFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("My Lists", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Organize characters and words for focused practice.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        when {
            isLoading -> {
                // Keep the content area stable while the first database read completes.
                Spacer(modifier = Modifier.weight(1f))
            }
            lists.isEmpty() -> {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Your lists will appear here", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Create a list to collect characters and words you want to review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(lists, key = { it.listId }) { list ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.List,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    list.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { onListContentsClick(list.listId) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) { Text("Contents") }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onPracticeListClick(list.listId) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) { Text("Practice") }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = { showCreateListDialog = true },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.large,
            enabled = !isCreating
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New List", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showCreateListDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreating) {
                    showCreateListDialog = false
                    newListName = ""
                }
            },
            title = { Text("New Practice List") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("List name") },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val name = newListName.trim()
                        if (name.isNotEmpty() && !isCreating) {
                            scope.launch {
                                isCreating = true
                                try {
                                    dao.insertList(PracticeList(name = name))
                                    lists = dao.getAllLists()
                                    newListName = ""
                                    showCreateListDialog = false
                                } finally {
                                    isCreating = false
                                }
                            }
                        }
                    })
                )
            },
            confirmButton = {
                Button(
                    enabled = newListName.isNotBlank() && !isCreating,
                    onClick = {
                        val name = newListName.trim()
                        scope.launch {
                            isCreating = true
                            try {
                                dao.insertList(PracticeList(name = name))
                                lists = dao.getAllLists()
                                newListName = ""
                                showCreateListDialog = false
                            } finally {
                                isCreating = false
                            }
                        }
                    }
                ) { Text(if (isCreating) "Creating…" else "Create") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isCreating) {
                            showCreateListDialog = false
                            newListName = ""
                        }
                    },
                    enabled = !isCreating
                ) { Text("Cancel") }
            }
        )
    }
}
