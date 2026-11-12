package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.AppDatabase

@Composable
fun ListPracticeScreen(
    listId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    var characters by remember(listId) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(listId) {
        characters = dao.getCharactersInList(listId)
    }

    when {
        characters == null -> {
            // No transient spinner; the startup preparation screen is reserved for first-run setup.
            Spacer(modifier = Modifier.fillMaxSize())
        }
        characters!!.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This list is empty.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) { Text("Go Back") }
                }
            }
        }
        else -> {
            PracticeScreen(
                wordToPractice = characters!!.joinToString(""),
                onBackClick = onBackClick
            )
        }
    }
}
