package com.infdesk.hanziwriting

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

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.Search) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { paddingValues ->
        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center // Center alignment for Search Bar
        ) {
            when (currentScreen) {
                Screen.Search -> SearchScreen()
                Screen.Lists -> Text("Lists Page", color = MaterialTheme.colorScheme.onBackground)
                Screen.Practice -> PracticeScreen()
                Screen.Settings -> Text("Settings Page", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
