package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current
    val reorderFallbackPx = with(density) { 88.dp.toPx() }

    var showCreateListDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var draggingListId by remember { mutableStateOf<Int?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var listPendingDeletion by remember { mutableStateOf<PracticeList?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    val currentLists = cachedLists.orEmpty()
    val latestLists = rememberUpdatedState(currentLists)
    val latestListsChanged = rememberUpdatedState(onListsChanged)

    fun saveOrder(order: List<PracticeList>) {
        onListsChanged(order)
        listPreferences.edit()
            .putString(LIST_ORDER_KEY, order.joinToString(",") { it.listId.toString() })
            .apply()
    }

    fun orderFromDatabase(databaseLists: List<PracticeList>): List<PracticeList> {
        val byId = databaseLists.associateBy { it.listId }
        val savedIds = listPreferences.getString(LIST_ORDER_KEY, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()

        if (savedIds.isEmpty()) return databaseLists.sortedByDescending { it.createdAt }

        val saved = savedIds.mapNotNull(byId::get)
        val savedSet = saved.map { it.listId }.toSet()
        val newlyAdded = databaseLists
            .filterNot { it.listId in savedSet }
            .sortedByDescending { it.createdAt }
        return newlyAdded + saved
    }

    // Refresh silently whenever the Lists page is entered. A cached snapshot stays
    // visible while Room is queried, so the page never flashes an empty/loading view.
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

    val moveList: (Int, Int) -> Unit = { listId, indexDirection ->
        val current = latestLists.value
        val from = current.indexOfFirst { it.listId == listId }
        val to = from + indexDirection
        if (from >= 0 && to in current.indices) {
            val reordered = current.toMutableList()
            val moved = reordered.removeAt(from)
            reordered.add(to, moved)
            val newOrder = reordered.toList()
            latestListsChanged.value(newOrder)
            listPreferences.edit()
                .putString(LIST_ORDER_KEY, newOrder.joinToString(",") { it.listId.toString() })
                .apply()
        }
    }
    val latestMoveList = rememberUpdatedState(moveList)

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
            cachedLists == null -> {
                // First visit only: reserve space until the first database snapshot arrives.
                Spacer(modifier = Modifier.weight(1f))
            }

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
                    "Long-press the grip and drag a list to reorder it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // `currentLists` is stored in bottom-to-top order. A normal LazyColumn
                // receives the reversed display copy, leaving the newest/first item
                // physically closest to the bottom of the screen.
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(currentLists.asReversed(), key = { it.listId }) { list ->
                        val isDragging = draggingListId == list.listId
                        val scale by animateFloatAsState(
                            targetValue = if (isDragging) 1.025f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "list-card-scale"
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onListContentsClick(list.listId) }
                                .animateItemPlacement(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                                .graphicsLayer {
                                    translationY = if (isDragging) draggedOffsetY else 0f
                                    scaleX = scale
                                    scaleY = scale
                                    shadowElevation = if (isDragging) 14.dp.toPx() else 1.dp.toPx()
                                }
                                .then(
                                    if (isDragging) Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                        shape = MaterialTheme.shapes.large
                                    ) else Modifier
                                ),
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDragging) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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
                                            var accumulatedDrag = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    accumulatedDrag = 0f
                                                    draggedOffsetY = 0f
                                                    draggingListId = list.listId
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    accumulatedDrag += dragAmount.y
                                                    val measuredRowHeight = listState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.key == list.listId }
                                                        ?.size?.toFloat() ?: reorderFallbackPx
                                                    val threshold = (measuredRowHeight * 0.52f).coerceAtLeast(1f)

                                                    // Move one row at a time. In the logical bottom-to-top
                                                    // order, dragging upward increases the stored index.
                                                    if (accumulatedDrag <= -threshold) {
                                                        latestMoveList.value(list.listId, +1)
                                                        accumulatedDrag += threshold
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } else if (accumulatedDrag >= threshold) {
                                                        latestMoveList.value(list.listId, -1)
                                                        accumulatedDrag -= threshold
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    draggedOffsetY = accumulatedDrag
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
                                    shape = RoundedCornerShape(16.dp),
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

                                // The card itself opens the contents page. Keep the two
                                // explicit actions on the trailing edge: Delete takes the
                                // former Practice position, while Practice sits at the edge.
                                IconButton(
                                    onClick = { listPendingDeletion = list }
                                ) {
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
                                val updated = latestLists.value.filterNot { it.listId == list.listId }
                                saveOrder(updated)
                                listPendingDeletion = null
                            } finally {
                                isDeleting = false
                            }
                        }
                    }
                ) { Text(if (isDeleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) }
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
