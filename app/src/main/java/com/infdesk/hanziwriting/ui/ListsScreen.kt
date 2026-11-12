package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.PracticeList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIST_ORDER_PREFS = "hanzi_list_order"
private const val LIST_ORDER_KEY = "bottom_to_top_ids"

@Composable
fun ListsScreen(
    onPracticeListClick: (Int) -> Unit,
    onListContentsClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val listPreferences = remember {
        context.getSharedPreferences(LIST_ORDER_PREFS, android.content.Context.MODE_PRIVATE)
    }
    val listState = rememberLazyListState()
    val dragHandleTopInsetPx = with(LocalDensity.current) { 10.dp.toPx() }

    // This list is stored in bottom-to-top order. With reverseLayout=true,
    // index zero is the item nearest the bottom edge / user's thumb.
    var bottomToTopLists by remember { mutableStateOf<List<PracticeList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var draggingListId by remember { mutableStateOf<Int?>(null) }
    val latestOrder = rememberUpdatedState(bottomToTopLists)

    fun persistOrder(order: List<PracticeList>) {
        bottomToTopLists = order
        listPreferences.edit()
            .putString(LIST_ORDER_KEY, order.joinToString(",") { it.listId.toString() })
            .apply()
    }

    fun mergeDatabaseLists(databaseLists: List<PracticeList>) {
        val byId = databaseLists.associateBy { it.listId }
        val savedIds = listPreferences.getString(LIST_ORDER_KEY, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()

        val restored = if (savedIds.isEmpty()) {
            databaseLists.sortedByDescending { it.createdAt }
        } else {
            val saved = savedIds.mapNotNull(byId::get)
            val savedSet = saved.map { it.listId }.toSet()
            val newlyAdded = databaseLists
                .filterNot { it.listId in savedSet }
                .sortedByDescending { it.createdAt }
            newlyAdded + saved
        }
        persistOrder(restored)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        runCatching { dao.getAllLists() }
            .onSuccess(::mergeDatabaseLists)
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
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        if (bottomToTopLists.isNotEmpty()) {
            Text(
                "Hold and drag the handle to change the order.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        when {
            isLoading -> {
                // Reserve the list area; avoid a short-lived loading spinner flash.
                Spacer(modifier = Modifier.weight(1f))
            }

            bottomToTopLists.isEmpty() -> {
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
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
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
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bottomToTopLists, key = { it.listId }) { list ->
                        val isDragging = draggingListId == list.listId
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isDragging) 3.dp else 1.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(44.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.pointerInput(list.listId) {
                                                var draggedCenterY = 0f
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { startOffset ->
                                                        draggingListId = list.listId
                                                        val itemInfo = listState.layoutInfo.visibleItemsInfo
                                                            .firstOrNull { it.key == list.listId }
                                                        draggedCenterY = (itemInfo?.offset ?: 0) + dragHandleTopInsetPx + startOffset.y
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        draggedCenterY += dragAmount.y
                                                        val targetInfo = listState.layoutInfo.visibleItemsInfo
                                                            .firstOrNull { info ->
                                                                draggedCenterY >= info.offset &&
                                                                    draggedCenterY <= info.offset + info.size
                                                            }
                                                        val targetId = targetInfo?.key as? Int
                                                        val current = latestOrder.value
                                                        if (targetId != null) {
                                                            val from = current.indexOfFirst { it.listId == list.listId }
                                                            val to = current.indexOfFirst { it.listId == targetId }
                                                            if (from >= 0 && to >= 0 && from != to) {
                                                                val reordered = current.toMutableList()
                                                                val moved = reordered.removeAt(from)
                                                                reordered.add(to, moved)
                                                                persistOrder(reordered)
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = { draggingListId = null },
                                                    onDragCancel = { draggingListId = null }
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder ${list.name}",
                                                tint = if (isDragging) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        list.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onListContentsClick(list.listId) },
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        shape = MaterialTheme.shapes.medium
                                    ) { Text("Contents") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onPracticeListClick(list.listId) },
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        shape = MaterialTheme.shapes.medium
                                    ) { Text("Practice") }
                                }
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
                androidx.compose.material3.OutlinedTextField(
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
                                    mergeDatabaseLists(dao.getAllLists())
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
                                mergeDatabaseLists(dao.getAllLists())
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
