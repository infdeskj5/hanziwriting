package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.PracticeList
import kotlinx.coroutines.launch

@Composable
fun ListsScreen(onPracticeListClick: (Int) -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val coroutineScope = rememberCoroutineScope()
    
    var lists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }

    // Load lists
    LaunchedEffect(Unit) {
        lists = dao.getAllLists()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Lists", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showCreateListDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create List")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(lists) { list ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPracticeListClick(list.listId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(list.name, style = MaterialTheme.typography.titleLarge)
                        Text("Practice >", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showCreateListDialog) {
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = { Text("New Practice List") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("List Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newListName.isNotBlank()) {
                        coroutineScope.launch {
                            dao.insertList(PracticeList(name = newListName))
                            lists = dao.getAllLists()
                            newListName = ""
                            showCreateListDialog = false
                        }
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
