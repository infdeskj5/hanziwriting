package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.PracticeList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIST_ORDER_PREFS = "hanzi_list_order"
private const val LIST_ORDER_KEY = "bottom_to_top_ids"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListsScreen(
    cachedLists: List<PracticeList>?,
    onListsChanged: (List<PracticeList>) -> Unit,
    onPracticeListClick: (Int) -> Unit,
    onListContentsClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val nameFocusRequester = remember { FocusRequester() }
    val listPreferences = remember {
        context.getSharedPreferences(LIST_ORDER_PREFS, android.content.Context.MODE_PRIVATE)
    }
    val listState = rememberLazyListState()

    var showCreateListDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var draggingListId by remember { mutableStateOf<Int?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var listPendingDeletion by remember { mutableStateOf<PracticeList?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    val currentLists = cachedLists.orEmpty() // Stored in visual bottom-to-top order.
    val latestLists = rememberUpdatedState(currentLists)
    val latestListsChanged = rememberUpdatedState(onListsChanged)

    fun saveOrder(orderBottomToTop: List<PracticeList>) {
        latestListsChanged.value(orderBottomToTop)
        listPreferences.edit()
            .putString(LIST_ORDER_KEY, orderBottomToTop.joinToString(",") { it.listId.toString() })
            .apply()
    }

    fun orderFromDatabase(databaseLists: List<PracticeList>): List<PracticeList> {
        val byId = databaseLists.associateBy { it.listId }
        val savedIds = listPreferences.getString(LIST_ORDER_KEY, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.distinct()
            .orEmpty()

        if (savedIds.isEmpty()) {
            // Oldest list is the first item in bottom-to-top order, hence appears at the bottom.
            return databaseLists.sortedWith(compareBy<PracticeList> { it.createdAt }.thenBy { it.listId })
        }

        val saved = savedIds.mapNotNull(byId::get)
        val savedSet = saved.map { it.listId }.toSet()
        val newlyAdded = databaseLists
            .filterNot { it.listId in savedSet }
            .sortedWith(compareBy<PracticeList> { it.createdAt }.thenBy { it.listId })

        // Keep saved order; append new lists so they appear above older entries.
        return saved + newlyAdded
    }

    LaunchedEffect(Unit) {
        runCatching { dao.getAllLists() }
            .onSuccess { saveOrder(orderFromDatabase(it)) }
    }

    LaunchedEffect(showCreateListDialog) {
        if (showCreateListDialog) {
            delay(100)
            nameFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val latestMoveList = rememberUpdatedState<(Int, Int) -> Unit> { listId, direction ->
        val current = latestLists.value
        val from = current.indexOfFirst { it.listId == listId }
        val to = from + direction
        if (from >= 0 && to in current.indices) {
            val reordered = current.toMutableList()
            val moved = reordered.removeAt(from)
            reordered.add(to, moved)
            saveOrder(reordered)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("My Lists", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your saved characters and words, ready for review.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        when {
            cachedLists == null -> Spacer(modifier = Modifier.weight(1f))

            currentLists.isEmpty() -> {
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
                            Icons.Default.FolderOpen,
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
                Text(
                    "Hold the grip and move a list up or down to reorder it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // The list state itself is bottom-to-top. reverseLayout places index 0 at the bottom.
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(currentLists, key = { it.listId }) { list ->
                        val isDragging = draggingListId == list.listId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                .clickable { onListContentsClick(list.listId) }
                                .graphicsLayer {
                                    translationY = if (isDragging) draggedOffsetY else 0f
                                    scaleX = if (isDragging) 1.015f else 1f
                                    scaleY = if (isDragging) 1.015f else 1f
                                    shadowElevation = if (isDragging) 10.dp.toPx() else 1.dp.toPx()
                                }
                                .then(
                                    if (isDragging) Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        shape = MaterialTheme.shapes.large
                                    ) else Modifier
                                ),
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 5.dp else 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDragging) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                } else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 78.dp)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(width = 42.dp, height = 48.dp)
                                        .pointerInput(list.listId) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedOffsetY = 0f
                                                    draggingListId = list.listId
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    draggedOffsetY += dragAmount.y

                                                    // The active card follows the finger. When its centre crosses
                                                    // the adjacent row's midpoint, swap the data order and offset
                                                    // the active card by the slot movement so it stays under the finger.
                                                    val direction = when {
                                                        dragAmount.y < 0f -> 1  // Up = next higher bottom-to-top index.
                                                        dragAmount.y > 0f -> -1
                                                        else -> 0
                                                    }
                                                    val ordered = latestLists.value
                                                    val fromIndex = ordered.indexOfFirst { it.listId == list.listId }
                                                    val targetIndex = fromIndex + direction
                                                    if (direction != 0 && fromIndex >= 0 && targetIndex in ordered.indices) {
                                                        val visible = listState.layoutInfo.visibleItemsInfo
                                                        val activeInfo = visible.firstOrNull { it.key == list.listId }
                                                        val targetInfo = visible.firstOrNull { it.index == targetIndex }
                                                        if (activeInfo != null && targetInfo != null) {
                                                            val activeCenter = activeInfo.offset + activeInfo.size / 2f
                                                            val targetCenter = targetInfo.offset + targetInfo.size / 2f
                                                            val midpoint = (activeCenter + targetCenter) / 2f
                                                            val draggedCenter = activeCenter + draggedOffsetY
                                                            val crossed = if (direction > 0) {
                                                                draggedCenter < midpoint
                                                            } else {
                                                                draggedCenter > midpoint
                                                            }
                                                            if (crossed) {
                                                                latestMoveList.value(list.listId, direction)
                                                                draggedOffsetY += activeInfo.offset - targetInfo.offset
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingListId = null
                                                    draggedOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    draggingListId = null
                                                    draggedOffsetY = 0f
                                                }
                                            )
                                        },
                                    shape = RoundedCornerShape(15.dp),
                                    color = if (isDragging) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Hold and drag to reorder ${list.name}",
                                            tint = if (isDragging) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = list.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )

                                IconButton(onClick = { listPendingDeletion = list }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete ${list.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                IconButton(onClick = { onPracticeListClick(list.listId) }) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = "Practice ${list.name}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
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
            enabled = !isCreating && !isDeleting
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
                                    saveOrder(orderFromDatabase(dao.getAllLists()))
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
                                saveOrder(orderFromDatabase(dao.getAllLists()))
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

    listPendingDeletion?.let { list ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) listPendingDeletion = null },
            title = { Text("Delete '${list.name}'?") },
            text = {
                Text("This permanently deletes the list and all its saved items. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            try {
                                dao.deleteListItems(list.listId)
                                dao.deletePracticeList(list.listId)
                                saveOrder(latestLists.value.filterNot { it.listId == list.listId })
                                listPendingDeletion = null
                            } finally {
                                isDeleting = false
                            }
                        }
                    }
                ) {
                    Text(if (isDeleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { listPendingDeletion = null }
                ) { Text("Cancel") }
            }
        )
    }
}
