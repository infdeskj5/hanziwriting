package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.ListItem
import com.infdesk.hanziwriting.data.PracticeList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddToListDialog(
    item: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { FocusRequester() }

    var lists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item) {
        isLoading = true
        lists = dao.getAllLists()
        isLoading = false
    }

    // The TextField is created only after showCreateDialog becomes true.
    // Wait for that composition before requesting focus and opening the IME.
    LaunchedEffect(showCreateDialog) {
        if (showCreateDialog) {
            delay(120)
            nameFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    fun createListAndSaveItem() {
        val name = newListName.trim()
        if (name.isEmpty() || isSaving) return

        scope.launch {
            isSaving = true
            saveError = null
            try {
                // Compare IDs so duplicate list names remain safe.
                val previousIds = dao.getAllLists().map { it.listId }.toSet()
                dao.insertList(PracticeList(name = name))
                val refreshedLists = dao.getAllLists()
                val createdList = refreshedLists.firstOrNull { it.listId !in previousIds }

                if (createdList == null) {
                    saveError = "The list was created, but it could not be identified for saving this item."
                    lists = refreshedLists
                    showCreateDialog = false
                } else {
                    dao.addCharacterToList(
                        ListItem(listId = createdList.listId, character = item)
                    )
                    lists = refreshedLists
                    newListName = ""
                    showCreateDialog = false
                    onDismiss()
                }
            } catch (error: Exception) {
                saveError = error.localizedMessage ?: "Couldn't create the list. Please try again."
            } finally {
                isSaving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                if (showCreateDialog) {
                    showCreateDialog = false
                    newListName = ""
                    saveError = null
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        label = { Text("List name") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { createListAndSaveItem() })
                    )
                    saveError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "'$item' will be added to this list automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column {
                    when {
                        isLoading -> {
                            Spacer(modifier = Modifier.fillMaxWidth().height(72.dp))
                        }
                        lists.isEmpty() -> {
                            Text(
                                "You don't have any lists yet. Create one to save this item.",
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
                                                try {
                                                    dao.addCharacterToList(
                                                        ListItem(listId = list.listId, character = item)
                                                    )
                                                    onDismiss()
                                                } finally {
                                                    isSaving = false
                                                }
                                            }
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(list.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
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
                    onClick = { createListAndSaveItem() }
                ) { Text(if (isSaving) "Saving…" else "Create and Add") }
            } else {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
            }
        },
        dismissButton = {
            if (showCreateDialog) {
                TextButton(
                    onClick = {
                        if (!isSaving) {
                            showCreateDialog = false
                            newListName = ""
                            saveError = null
                        }
                    },
                    enabled = !isSaving
                ) { Text("Back") }
            }
        }
    )
}
