package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp // <-- ADDED THIS MISSING IMPORT
import com.infdesk.hanziwriting.data.AppDatabase
import kotlinx.coroutines.launch

@Composable
fun ListPracticeScreen(
    listId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val coroutineScope = rememberCoroutineScope()
    
    var characters by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(listId) {
        coroutineScope.launch {
            characters = dao.getCharactersInList(listId)
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (characters.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This list is empty.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackClick) { Text("Go Back") }
            }
        }
    } else {
        // Reuse the main PracticeScreen with the list joined as a single string.
        // This applies the same 5-phase interleaved learning to lists!
        PracticeScreen(
            wordToPractice = characters.joinToString(""),
            onBackClick = onBackClick
        )
    }
}
