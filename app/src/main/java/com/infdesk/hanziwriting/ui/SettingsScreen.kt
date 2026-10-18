package com.infdesk.hanziwriting.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.infdesk.hanziwriting.utils.CrashHandler
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var showExportSuccess by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 32.dp))

        val crashLogs = CrashHandler.getCrashLogs()
        
        Button(
            onClick = {
                if (crashLogs.isNotEmpty()) {
                    val combinedFile = File(context.cacheDir, "all_crash_logs.txt")
                    combinedFile.writeText(crashLogs.joinToString("\n\n---\n\n") { it.readText() })
                    
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        combinedFile
                    )
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Crash Logs"))
                    CrashHandler.clearCrashLogs()
                    exportMessage = "Logs exported successfully!"
                } else {
                    exportMessage = "No crash logs found."
                }
                showExportSuccess = true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Export Crash Logs (${crashLogs.size})")
        }

        if (showExportSuccess) {
            Text(
                exportMessage,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Text("Hanzi Writing v1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
