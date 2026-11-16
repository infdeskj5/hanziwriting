package com.infdesk.hanziwriting.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.ListItem
import com.infdesk.hanziwriting.data.PracticeList
import com.infdesk.hanziwriting.utils.DebugLog
import com.infdesk.hanziwriting.utils.Diagnostics
import kotlinx.coroutines.launch
import kotlinx.coroutines.withFrameNanos

@Composable
fun ListContentsScreen(
    listId: Int,
    selectedItems: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onBackClick: () -> Unit,
    onWordClick: (String) -> Unit,
    onPracticeItemClick: (String) -> Unit,
    onPracticeSelectedClick: (List<String>) -> Unit
) {
    BackHandler(enabled = true, onBack = onBackClick)
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var characters by remember(listId) { mutableStateOf<List<String>>(emptyList()) }
    var listName by remember(listId) { mutableStateOf("List contents") }
    var isLoading by remember(listId) { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showAddSelectedDialog by remember { mutableStateOf(false) }
    var destinationLists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }

    DisposableEffect(listId) {
        onDispose { DebugLog.d("Probe", "ListContents left composition list=$listId") }
    }

    // The list contains at most a few thousand short strings. Loading this small
    // projection once is cheaper and smoother than fetching another OFFSET page
    // during a fast scroll. LazyColumn still composes only visible rows.
    LaunchedEffect(listId) {
        val openSeq = Diagnostics.openSequence(listId)
        val sinceTap = Diagnostics.sinceMark("listTap:$listId")
        val t0 = SystemClock.elapsedRealtime()
        isLoading = true
        try {
            val tQ0 = SystemClock.elapsedRealtime()
            val lists = dao.getAllLists()
            val raw = dao.getCharactersInListInInsertionOrder(listId)
            val tQ1 = SystemClock.elapsedRealtime()
            val loadedCharacters = raw.distinct()
            val tP1 = SystemClock.elapsedRealtime()
            listName = lists.firstOrNull { it.listId == listId }?.name ?: "List contents"
            characters = loadedCharacters
            onSelectionChange(selectedItems.intersect(loadedCharacters.toSet()))
            isLoading = false
            // Wait for the first frame that actually shows the rows.
            withFrameNanos { }
            val tFirst = SystemClock.elapsedRealtime()
            DebugLog.d(
                "ListContents",
                "list=$listId open=#$openSeq sinceTapMs=$sinceTap rows=${raw.size} " +
                    "distinct=${loadedCharacters.size} queryMs=${tQ1 - tQ0} prepMs=${tP1 - tQ1} " +
                    "firstFrameMs=${tFirst - t0} totalMs=${tFirst - t0}"
            )
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(showAddSelectedDialog) {
        if (showAddSelectedDialog) {
            destinationLists = dao.getAllLists().filterNot { it.listId == listId }
        }
    }

    val selectedInList = remember(characters, selectedItems) {
        characters.filter { it in selectedItems }
    }
    val canAct = selectedInList.isNotEmpty() && !isBusy
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
            isLoading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
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
                OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Lists")
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    // Item 0 is anchored to the bottom. This preserves the list's
                    // established bottom-first ordering without OFFSET page loads.
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(characters, key = { it }) { item ->
                        val isSelected = item in selectedItems
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onPracticeItemClick(item) },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Practice $item",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onWordClick(item) },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "View details for $item",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    onSelectionChange(
                                        if (checked) selectedItems + item else selectedItems - item
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = canAct,
                        modifier = Modifier.weight(1.15f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error, maxLines = 1)
                    }
                    TextButton(
                        onClick = { onSelectionChange(characters.toSet()) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) { Text("Select all", maxLines = 1) }
                    TextButton(
                        onClick = { onSelectionChange(emptySet()) },
                        enabled = selectedInList.isNotEmpty() && !isBusy,
                        modifier = Modifier.weight(0.8f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) { Text("Clear", maxLines = 1) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedIconButton(
                        onClick = { showAddSelectedDialog = true },
                        enabled = canAct,
                        modifier = Modifier.size(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add selected items to another list")
                    }
                    Button(
                        onClick = { onPracticeSelectedClick(selectedInList) },
                        enabled = canAct,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Practice Selected (${selectedInList.size})")
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteConfirmation = false },
            title = { Text("Remove selected items?") },
            text = {
                Text("${selectedInList.size} selected item(s) will be removed from '$listName'.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        val toDelete = selectedInList.toList()
                        scope.launch {
                            isBusy = true
                            try {
                                val deleteSet = toDelete.toSet()
                                toDelete.chunked(400).forEach { chunk ->
                                    dao.deleteSelectedListItems(listId, chunk)
                                }
                                characters = characters.filterNot { it in deleteSet }
                                onSelectionChange(selectedItems - deleteSet)
                                showDeleteConfirmation = false
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) {
                    Text(if (isBusy) "Removing…" else "Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isBusy
                ) { Text("Cancel") }
            }
        )
    }

    if (showAddSelectedDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showAddSelectedDialog = false },
            title = { Text("Add selected items") },
            text = {
                Column {
                    Text(
                        "Add ${selectedInList.size} selected item(s) to another practice list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (destinationLists.isEmpty()) {
                        Text("There are no other lists yet.")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(destinationLists, key = { it.listId }) { destination ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isBusy) {
                                            val selectedSnapshot = selectedInList.toList()
                                            scope.launch {
                                                isBusy = true
                                                try {
                                                    selectedSnapshot.chunked(400).forEach { chunk ->
                                                        dao.addCharactersToList(
                                                            chunk.map { character ->
                                                                ListItem(destination.listId, character)
                                                            }
                                                        )
                                                    }
                                                    showAddSelectedDialog = false
                                                    Toast.makeText(
                                                        context,
                                                        "Added ${selectedSnapshot.size} item(s) to '${destination.name}'.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } finally {
                                                    isBusy = false
                                                }
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(destination.name, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (!isBusy) showAddSelectedDialog = false },
                    enabled = !isBusy
                ) { Text("Close") }
            }
        )
    }
}
