package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.ListItem
import com.infdesk.hanziwriting.data.PracticeList
import kotlinx.coroutines.launch

@Composable
fun AddToListDialog(
    item: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val scope = rememberCoroutineScope()

    var lists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(item) {
        isLoading = true
        lists = dao.getAllLists()
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                if (showCreateDialog) {
                    showCreateDialog = false
                    newListName = ""
                } else {
                    onDismiss()
                }
            }
        },
        title = {
            Text(if (showCreateDialog) "Create List" else "Add '$item' to a List")
        },
        text = {
            if (showCreateDialog) {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        lists.isEmpty() -> {
                            Text(
                                "You don't have any lists yet. Create a list to save this item.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        else -> {
                            Text(
                                "Choose a list:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(lists, key = { it.listId }) { list ->
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                isSaving = true
                                                dao.addCharacterToList(
                                                    ListItem(
                                                        listId = list.listId,
                                                        character = item
                                                    )
                                                )
                                                isSaving = false
                                                onDismiss()
                                            }
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            list.name,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create List")
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateDialog) {
                TextButton(
                    enabled = newListName.isNotBlank() && !isSaving,
                    onClick = {
                        val name = newListName.trim()
                        scope.launch {
                            isSaving = true
                            dao.insertList(PracticeList(name = name))
                            lists = dao.getAllLists()
                            newListName = ""
                            showCreateDialog = false
                            isSaving = false
                        }
                    }
                ) {
                    Text("Create")
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        },
        dismissButton = {
            if (showCreateDialog) {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newListName = ""
                    },
                    enabled = !isSaving
                ) {
                    Text("Back")
                }
            }
        }
    )
}
