package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.HanziCharacter
import com.infdesk.hanziwriting.utils.AppState

enum class Screen(val title: String, val icon: ImageVector) {
    Search("Search", Icons.Default.Search),
    Lists("Lists", Icons.Default.List),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    if (AppState.isInitializing) {
        LoadingScreen()
    } else {
        HanziWritingApp()
    }
}

@Composable
fun LoadingScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hanzi Writing", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(modifier = Modifier.fillMaxWidth(AppState.initProgress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(AppState.initMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HanziWritingApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Search) }
    var wordBackStack by remember { mutableStateOf<List<String>>(emptyList()) }  // STACK, not a single value
    var practiceCharacter by remember { mutableStateOf<String?>(null) }
    var practiceListId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<HanziCharacter>>(emptyList()) }

    val currentWord = wordBackStack.lastOrNull()

    val displayState = when {
        practiceListId != null -> "ListPractice"
        practiceCharacter != null -> "Practice"
        currentWord != null -> "WordPage"
        else -> currentScreen.name
    }

    Scaffold(bottomBar = {
        if (displayState == "Search" || displayState == "Lists" || displayState == "Settings") {
            NavigationBar {
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
                        }
                    )
                }
            }
        }
    }) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            when (displayState) {
                "Search" -> SearchScreen(
                    searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it },
                    searchResults = searchResults, onResultsChange = { searchResults = it },
                    onWordClick = { word -> wordBackStack = listOf(word) }   // start a new stack
                )
                "WordPage" -> WordPage(
                    word = currentWord!!,
                    onBackClick = { wordBackStack = wordBackStack.dropLast(1) },   // POP -> previous word or search
                    onComponentClick = { comp -> wordBackStack = wordBackStack + comp },  // PUSH
                    onPracticeClick = { word -> practiceCharacter = word }
                )
                "Practice" -> PracticeScreen(wordToPractice = practiceCharacter, onBackClick = { practiceCharacter = null })
                "Lists" -> ListsScreen(onPracticeListClick = { listId -> practiceListId = listId })
                "ListPractice" -> ListPracticeScreen(listId = practiceListId!!, onBackClick = { practiceListId = null })
                "Settings" -> SettingsScreen()
            }
        }
    }
}
