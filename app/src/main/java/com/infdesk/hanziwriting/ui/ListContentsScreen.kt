package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase

@Composable
fun ListContentsScreen(
    listId: Int,
    selectedItems: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onBackClick: () -> Unit,
    onPracticeSelectedClick: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }

    var characters by remember(listId) { mutableStateOf<List<String>>(emptyList()) }
    var listName by remember(listId) { mutableStateOf("List contents") }
    var isLoading by remember(listId) { mutableStateOf(true) }

    LaunchedEffect(listId) {
        isLoading = true
        val allLists = dao.getAllLists()
        listName = allLists.firstOrNull { it.listId == listId }?.name ?: "List contents"
        characters = dao.getCharactersInList(listId).distinct()
        isLoading = false

        // Remove stale selections if list contents have changed.
        onSelectionChange(selectedItems.intersect(characters.toSet()))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to lists")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(listName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${characters.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            characters.isEmpty() -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "This list is empty. Add characters or words from Search or Word Details.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onSelectionChange(characters.toSet()) }) {
                        Text("Select all")
                    }
                    TextButton(onClick = { onSelectionChange(emptySet()) }) {
                        Text("Clear")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(characters, key = { index, value -> "$index-$value" }) { _, item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = item in selectedItems,
                                    onCheckedChange = { checked ->
                                        onSelectionChange(
                                            if (checked) selectedItems + item
                                            else selectedItems - item
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val selected = characters.filter { it in selectedItems }
                        if (selected.isNotEmpty()) onPracticeSelectedClick(selected)
                    },
                    enabled = selectedItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Practice Selected (${selectedItems.size})")
                }
            }
        }

        if (!isLoading && characters.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Lists")
            }
        }
    }
}
