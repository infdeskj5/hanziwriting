package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    
    var listName by remember { mutableStateOf("") }
    var characters by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(listId) {
        coroutineScope.launch {
            val list = dao.getAllLists().find { it.listId == listId }
            listName = list?.name ?: "Practice List"
            characters = dao.getCharactersInList(listId)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(listName, style = MaterialTheme.typography.titleLarge)
                if (characters.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = (currentIndex + 1).toFloat() / characters.size,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
            if (characters.isNotEmpty()) {
                Text(
                    "${currentIndex + 1}/${characters.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SAFE PATTERN: Use if/else instead of early return
        if (characters.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This list is empty.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) { Text("Go Back") }
                }
            }
        } else {
            val currentChar = characters[currentIndex]
            
            val practiceViewModel = remember(currentChar) { 
                PracticeViewModel(AppDatabase.getDatabase(context)) 
            }
            
            LaunchedEffect(currentChar) {
                practiceViewModel.loadCharacter(currentChar)
            }

            PracticeScreenContent(
                viewModel = practiceViewModel,
                onNext = {
                    if (currentIndex < characters.size - 1) currentIndex++
                },
                onPrev = {
                    if (currentIndex > 0) currentIndex--
                }
            )
        }
    }
}

@Composable
fun PracticeScreenContent(
    viewModel: PracticeViewModel,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val data = viewModel.practiceData

    // SAFE PATTERN: Wrap in if instead of early return
    if (data != null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(data.character, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                    Text(data.pinyin, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(data.definition, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HanziCanvas(viewModel = viewModel, modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Stroke: ${viewModel.currentStrokeIndex + 1}/${data.totalStrokes}", style = MaterialTheme.typography.bodyLarge)
                    Text("Errors: ${viewModel.totalErrors}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }

                Row {
                    OutlinedButton(
                        onClick = onPrev,
                        enabled = viewModel.currentStrokeIndex == 0 && viewModel.totalErrors == 0
                    ) {
                        Text("Prev")
                    }
                    Spacer(Modifier.width(16.dp))
                    
                    if (viewModel.isCompleted) {
                        Button(onClick = onNext) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Next")
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.resetPractice() }) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
