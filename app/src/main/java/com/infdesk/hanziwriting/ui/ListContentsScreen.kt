package com.infdesk.hanziwriting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import kotlinx.coroutines.launch

@Composable
fun ListContentsScreen(
    listId: Int,
    selectedItems: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onBackClick: () -> Unit,
    onPracticeSelectedClick: (List<String>) -> Unit
) {
    BackHandler(enabled = true, onBack = onBackClick)

    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val scope = rememberCoroutineScope()

    var characters by remember(listId) { mutableStateOf<List<String>>(emptyList()) }
    var listName by remember(listId) { mutableStateOf("List contents") }
    var isLoading by remember(listId) { mutableStateOf(true) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(listId) {
        isLoading = true
        val allLists = dao.getAllLists()
        listName = allLists.firstOrNull { it.listId == listId }?.name ?: "List contents"
        // DAO returns insertion order. With reverseLayout=true the oldest saved item
        // is anchored to the bottom and newer items are stacked above it.
        characters = dao.getCharactersInList(listId).distinct()
        isLoading = false
        onSelectionChange(selectedItems.intersect(characters.toSet()))
    }

    val selectedInDisplayOrder = characters.filter { it in selectedItems }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to lists")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(listName, style = MaterialTheme.typography.headlineSmall)
                if (!isLoading) {
                    Text(
                        "${characters.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            isLoading -> Spacer(modifier = Modifier.weight(1f))

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
                OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Lists")
                }
            }

            else -> {
                // Keep the database order as bottom-to-top. Render a reversed visual copy
                // in a normal LazyColumn and bottom-align short lists. This avoids relying
                // on reverseLayout's scroll-start semantics for the visual order.
                val visualCharacters = characters.asReversed()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, alignment = Alignment.Bottom)
                ) {
                    items(visualCharacters, key = { it }) { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (item in selectedItems) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                            } else MaterialTheme.colorScheme.surfaceVariant
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
                                            if (checked) selectedItems + item else selectedItems - item
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bulk actions are fixed above the primary practice action for thumb reach.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = selectedInDisplayOrder.isNotEmpty() && !isDeleting,
                        modifier = Modifier.weight(1.15f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error, maxLines = 1)
                    }
                    TextButton(
                        onClick = { onSelectionChange(characters.toSet()) },
                        enabled = !isDeleting,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) { Text("Select all", maxLines = 1) }
                    TextButton(
                        onClick = { onSelectionChange(emptySet()) },
                        enabled = selectedItems.isNotEmpty() && !isDeleting,
                        modifier = Modifier.weight(0.8f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) { Text("Clear", maxLines = 1) }
                }

                Button(
                    onClick = {
                        if (selectedInDisplayOrder.isNotEmpty()) {
                            onPracticeSelectedClick(selectedInDisplayOrder)
                        }
                    },
                    enabled = selectedInDisplayOrder.isNotEmpty() && !isDeleting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Practice Selected (${selectedInDisplayOrder.size})")
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmation = false },
            title = { Text("Remove selected items?") },
            text = {
                Text("${selectedInDisplayOrder.size} selected item(s) will be removed from '$listName'.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        val toDelete = selectedInDisplayOrder.toList()
                        scope.launch {
                            isDeleting = true
                            try {
                                dao.deleteSelectedListItems(listId, toDelete)
                                characters = characters.filterNot { it in toDelete }
                                onSelectionChange(selectedItems - toDelete.toSet())
                                showDeleteConfirmation = false
                            } finally {
                                isDeleting = false
                            }
                        }
                    }
                ) {
                    Text(if (isDeleting) "Removing…" else "Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isDeleting
                ) { Text("Cancel") }
            }
        )
    }
}
