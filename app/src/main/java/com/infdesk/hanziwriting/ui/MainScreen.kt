package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.infdesk.hanziwriting.utils.AppState

enum class Screen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
fun HanziWritingApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Search) }
    var wordToView by remember { mutableStateOf<String?>(null) }
    var practiceCharacter by remember { mutableStateOf<String?>(null) }
    var practiceListId by remember { mutableStateOf<Int?>(null) }

    val displayState = when {
        practiceListId != null -> "ListPractice"
        practiceCharacter != null -> "Practice"
        wordToView != null -> "WordPage"
        else -> currentScreen.name
    }

    Scaffold(
        bottomBar = {
            if (displayState == "Search" || displayState == "Lists" || displayState == "Settings") {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentScreen == screen,
                            onClick = {
                                currentScreen = screen
                                wordToView = null
                                practiceCharacter = null
                                practiceListId = null
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            when (displayState) {
                "Search" -> SearchScreen(onWordClick = { word -> wordToView = word })
                "WordPage" -> WordPage(
                    word = wordToView!!,
                    onBackClick = { wordToView = null },
                    onComponentClick = { comp -> wordToView = comp },
                    onPracticeClick = { word -> practiceCharacter = word }
                )
                "Practice" -> PracticeScreen(
                    wordToPractice = practiceCharacter,
                    onBackClick = { practiceCharacter = null }
                )
                "Lists" -> ListsScreen(onPracticeListClick = { listId -> practiceListId = listId })
                "ListPractice" -> ListPracticeScreen(
                    listId = practiceListId!!,
                    onBackClick = { practiceListId = null }
                )
                "Settings" -> SettingsScreen()
            }
        }
    }
}
