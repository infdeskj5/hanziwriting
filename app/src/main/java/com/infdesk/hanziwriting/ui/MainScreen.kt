package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.data.PracticeList
import com.infdesk.hanziwriting.utils.AppState

enum class Screen(val title: String, val icon: ImageVector) {
    Search("Search", Icons.Default.Search),
    Lists("Lists", Icons.Default.List),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    HanziWritingApp()
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Hanzi Writing",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(AppState.initProgress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            AppState.initMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InitializationFailureScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Couldn't prepare Hanzi Writing", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            AppState.initMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun HanziWritingApp() {
    val context = LocalContext.current
    val listDao = remember { AppDatabase.getDatabase(context).hanziDao() }
    var cachedLists by remember { mutableStateOf<List<PracticeList>?>(null) }

    fun applySavedListOrder(databaseLists: List<PracticeList>): List<PracticeList> {
        val orderPreferences = context.getSharedPreferences(
            "hanzi_list_order",
            android.content.Context.MODE_PRIVATE
        )
        val savedIds = orderPreferences.getString("bottom_to_top_ids", null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.distinct()
            .orEmpty()
        val byId = databaseLists.associateBy { it.listId }
        val saved = savedIds.mapNotNull(byId::get)
        val savedSet = saved.map { it.listId }.toSet()
        val additions = databaseLists
            .filterNot { it.listId in savedSet }
            .sortedWith(compareBy<PracticeList> { it.createdAt }.thenBy { it.listId })
        return if (savedIds.isEmpty()) {
            databaseLists.sortedWith(compareBy<PracticeList> { it.createdAt }.thenBy { it.listId })
        } else saved + additions
    }

    LaunchedEffect(Unit) {
        runCatching { listDao.getAllLists() }
            .onSuccess { cachedLists = applySavedListOrder(it) }
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Search) }
    var wordBackStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var practiceCharacter by remember { mutableStateOf<String?>(null) }
    var practiceListId by remember { mutableStateOf<Int?>(null) }
    var listContentsId by remember { mutableStateOf<Int?>(null) }

    // Keep selected entries when returning from custom practice to list contents.
    var selectedListItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customPracticeItems by remember { mutableStateOf<List<String>?>(null) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<HanziCharacter>>(emptyList()) }
    // Search state lives above SearchScreen so opening Word Details does not discard
    // the completed query, results, or the user's scroll position.
    var completedSearchQuery by remember { mutableStateOf("") }
    val searchResultsListState = rememberLazyListState()
    // Keep Lists scroll position alive while List Contents is displayed.
    val listsLazyListState = rememberLazyListState()

    val currentWord = wordBackStack.lastOrNull()
    val displayState = when {
        customPracticeItems != null -> "CustomListPractice"
        practiceListId != null -> "ListPractice"
        practiceCharacter != null -> "Practice"
        // A word opened from List Contents temporarily takes over this route.
        // Popping its wordBackStack returns to the still-selected list.
        currentWord != null -> "WordPage"
        listContentsId != null -> "ListContents"
        else -> currentScreen.name
    }

    var previousDisplayState by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(displayState) {
        // List Contents cannot create/delete a list, so returning from it should reuse
        // the existing snapshot and LazyColumn state without a database refresh.
        // Other routes may create lists, so refresh when returning from those routes.
        val returningFromListContents =
            displayState == "Lists" && previousDisplayState == "ListContents"

        if (displayState == "Lists" && !returningFromListContents) {
            runCatching { listDao.getAllLists() }
                .onSuccess { cachedLists = applySavedListOrder(it) }
        }

        previousDisplayState = displayState
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (displayState == "Search" || displayState == "Lists" || displayState == "Settings") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp
                ) {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentScreen == screen,
                            onClick = {
                                currentScreen = screen
                                wordBackStack = emptyList()
                                practiceCharacter = null
                                practiceListId = null
                                listContentsId = null
                                selectedListItems = emptySet()
                                customPracticeItems = null
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // Deliberately switch pages directly instead of Crossfade. Crossfade keeps
        // both pages composed for its duration; WordPage contains looping stroke
        // animations, so that doubles rendering work during navigation and caused
        // avoidable frame drops on mid-range devices.
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when (displayState) {
                "Search" -> SearchScreen(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchResults = searchResults,
                    onResultsChange = { searchResults = it },
                    completedSearchQuery = completedSearchQuery,
                    onSearchCompleted = { completedSearchQuery = it },
                    resultsListState = searchResultsListState,
                    onWordClick = { wordBackStack = listOf(it) }
                )

                "WordPage" -> WordPage(
                    word = currentWord.orEmpty(),
                    onBackClick = { wordBackStack = wordBackStack.dropLast(1) },
                    onComponentClick = { comp -> wordBackStack = wordBackStack + comp },
                    onPracticeClick = { practiceCharacter = it }
                )

                "Practice" -> PracticeScreen(
                    wordToPractice = practiceCharacter,
                    onBackClick = { practiceCharacter = null }
                )

                "Lists" -> ListsScreen(
                    cachedLists = cachedLists,
                    listState = listsLazyListState,
                    onListsChanged = { cachedLists = it },
                    onPracticeListClick = { practiceListId = it },
                    onListContentsClick = {
                        selectedListItems = emptySet()
                        listContentsId = it
                    }
                )

                "ListContents" -> {
                    val activeListId = listContentsId
                    if (activeListId != null) {
                        ListContentsScreen(
                            listId = activeListId,
                            selectedItems = selectedListItems,
                            onSelectionChange = { selectedListItems = it },
                            onBackClick = {
                                listContentsId = null
                                selectedListItems = emptySet()
                            },
                            onWordClick = { item -> wordBackStack = listOf(item) },
                            onPracticeItemClick = { item -> practiceCharacter = item },
                            onPracticeSelectedClick = { selected -> customPracticeItems = selected }
                        )
                    } else {
                        Spacer(Modifier.fillMaxSize())
                    }
                }

                "CustomListPractice" -> PracticeScreen(
                    wordToPractice = customPracticeItems.orEmpty().joinToString(""),
                    onBackClick = { customPracticeItems = null }
                )

                "ListPractice" -> {
                    val activeListId = practiceListId
                    if (activeListId != null) {
                        ListPracticeScreen(
                            listId = activeListId,
                            onBackClick = { practiceListId = null }
                        )
                    } else {
                        Spacer(Modifier.fillMaxSize())
                    }
                }

                "Settings" -> SettingsScreen()
                else -> SearchScreen(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchResults = searchResults,
                    onResultsChange = { searchResults = it },
                    completedSearchQuery = completedSearchQuery,
                    onSearchCompleted = { completedSearchQuery = it },
                    resultsListState = searchResultsListState,
                    onWordClick = { wordBackStack = listOf(it) }
                )
            }
        }
    }
}
