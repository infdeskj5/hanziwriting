package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    Search("Search", Icons.Default.Search),
    Lists("Lists", Icons.Default.List),
    Practice("Practice", Icons.Default.Edit),
    Settings("Settings", Icons.Default.Settings)
}

// Add this to your Screen enum or handle as a separate route
// For simplicity, let's pass a callback
@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.Search) }
    var practiceCharacter by remember { mutableStateOf<String?>(null) }

    // If practiceCharacter is set, override the screen to Practice
    val displayScreen = if (practiceCharacter != null) Screen.Practice else currentScreen

    Scaffold(
        bottomBar = {
            // Hide bottom bar if currently practicing to give full focus, 
            // or keep it if you prefer. Let's keep it but highlight Practice.
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = displayScreen == screen,
                        onClick = { 
                            currentScreen = screen
                            if (screen != Screen.Practice) practiceCharacter = null 
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (displayScreen) {
                Screen.Search -> SearchScreen(onPracticeClick = { char -> 
                    practiceCharacter = char
                    currentScreen = Screen.Practice 
                })
                Screen.Practice -> PracticeScreen(characterToPractice = practiceCharacter)
                Screen.Lists -> Text("Lists Page")
                Screen.Settings -> Text("Settings Page")
            }
        }
    }
}
