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
import com.infdesk.hanziwriting.utils.DebugLog
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 32.dp))

        Button(
            onClick = {
                val sb = StringBuilder()
                sb.append("=== DEBUG LOG ===\n")
                DebugLog.getLogFile()?.let {
                    if (it.exists()) sb.append(it.readText()) else sb.append("(empty)")
                }
                val crashLogs = CrashHandler.getCrashLogs()
                sb.append("\n\n=== CRASH LOGS (${crashLogs.size}) ===\n")
                if (crashLogs.isEmpty()) sb.append("(none)")
                else crashLogs.forEach { sb.append(it.readText()).append("\n---\n") }

                val report = File(context.cacheDir, "hanzi_report.txt")
                report.writeText(sb.toString())

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Export Hanzi Report"))
                CrashHandler.clearCrashLogs()
                exportMessage = "Report exported!"
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Export Logs & Debug Report")
        }

        exportMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(modifier = Modifier.weight(1f))
        Text("Hanzi Writing v1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
