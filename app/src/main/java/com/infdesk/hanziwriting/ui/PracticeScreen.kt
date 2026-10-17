package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infdesk.hanziwriting.data.AppDatabase

@Composable
fun PracticeScreen(
    characterToPractice: String? = null,
    onBackClick: () -> Unit = {}, // FIXED: Added this parameter
    viewModel: PracticeViewModel = viewModel(
        factory = PracticeViewModelFactory(AppDatabase.getDatabase(LocalContext.current))
    )
) {
    LaunchedEffect(characterToPractice) {
        if (characterToPractice != null) {
            viewModel.loadCharacter(characterToPractice)
        }
    }

    val data = viewModel.practiceData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (data == null) {
            Text("Select a character from Search to practice.", textAlign = TextAlign.Center)
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.character, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text(data.pinyin, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.definition, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                if (data.radical != null) {
                    Text("Radical: ${data.radical}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
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
                    onClick = { viewModel.resetPractice() },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
                Spacer(Modifier.width(16.dp))
                
                if (viewModel.isCompleted) {
                    Button(
                        onClick = onBackClick, // FIXED: Use onBackClick to exit
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Done!")
                    }
                }
            }
        }
    }
}

class PracticeViewModelFactory(private val database: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PracticeViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
