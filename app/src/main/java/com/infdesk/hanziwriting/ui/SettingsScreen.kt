package com.infdesk.hanziwriting.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.ListItem
import com.infdesk.hanziwriting.data.PracticeList
import com.infdesk.hanziwriting.utils.CrashHandler
import com.infdesk.hanziwriting.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LIST_BACKUP_VERSION = 1

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val exportListsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                statusMessage = null
                try {
                    val count = withContext(Dispatchers.IO) {
                        val root = JsonObject().apply {
                            addProperty("app", "Hanzi Writing")
                            addProperty("formatVersion", LIST_BACKUP_VERSION)
                            addProperty("exportedAt", System.currentTimeMillis())
                        }
                        val listArray = JsonArray()
                        val lists = dao.getAllLists()
                        for (list in lists) {
                            val entry = JsonObject().apply {
                                addProperty("name", list.name)
                                addProperty("createdAt", list.createdAt)
                                add("characters", JsonArray().also { chars ->
                                    dao.getCharactersInList(list.listId).forEach { character -> chars.add(character) }
                                })
                            }
                            listArray.add(entry)
                        }
                        root.add("lists", listArray)
                        val json = GsonBuilder().setPrettyPrinting().create().toJson(root)
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Couldn't open the selected destination.")
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                        lists.size
                    }
                    statusMessage = "Exported $count list(s)."
                } catch (error: Exception) {
                    statusMessage = "Export failed: ${error.localizedMessage ?: "Unknown error"}"
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val importListsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                statusMessage = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        val jsonText = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?: error("Couldn't read the selected file.")
                        val root = JsonParser.parseString(jsonText).asJsonObject
                        val version = root.get("formatVersion")?.asInt
                            ?: error("This file isn't a Hanzi Writing list backup.")
                        require(version == LIST_BACKUP_VERSION) {
                            "Unsupported backup version: $version"
                        }
                        val listArray = root.getAsJsonArray("lists")
                            ?: error("The backup doesn't contain a lists array.")

                        var listsProcessed = 0
                        var newItems = 0
                        for (element in listArray) {
                            if (!element.isJsonObject) continue
                            val entry = element.asJsonObject
                            val name = entry.get("name")?.takeUnless { it.isJsonNull }
                                ?.asString?.trim().orEmpty()
                            if (name.isBlank()) continue

                            val createdAt = entry.get("createdAt")
                                ?.takeUnless { it.isJsonNull }?.asLong
                                ?: System.currentTimeMillis()

                            val existingList = dao.getListByName(name)
                            val targetListId = existingList?.listId ?: run {
                                val insertedId = dao.insertList(
                                    PracticeList(name = name, createdAt = createdAt)
                                )
                                if (insertedId > 0L) insertedId.toInt()
                                else dao.getListByName(name)?.listId
                                    ?: error("Couldn't create or locate list '$name'.")
                            }

                            val alreadyPresent = if (existingList != null) {
                                dao.getCharactersInList(targetListId).toSet()
                            } else emptySet()
                            val characters = entry.getAsJsonArray("characters")
                                ?.mapNotNull { value ->
                                    runCatching { value.asString.trim() }.getOrNull()
                                }
                                ?.filter { it.isNotEmpty() }
                                ?.distinct()
                                .orEmpty()

                            characters.forEach { character ->
                                dao.addCharacterToList(
                                    ListItem(listId = targetListId, character = character)
                                )
                                if (character !in alreadyPresent) newItems++
                            }
                            listsProcessed++
                        }
                        "Imported/merged $listsProcessed list(s) and $newItems item(s)."
                    }
                    statusMessage = result
                } catch (error: Exception) {
                    statusMessage = "Import failed: ${error.localizedMessage ?: "Invalid backup file"}"
                } finally {
                    isBusy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Back up your study lists and share diagnostics when troubleshooting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Hanzi Writing · v1.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.weight(1f))

        statusMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Import failed") || it.startsWith("Export failed")) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    isBusy = true
                    statusMessage = null
                    try {
                        val report = withContext(Dispatchers.IO) {
                            val sb = StringBuilder()
                            sb.append("=== HANZI WRITING DEBUG LOG ===\n")
                            DebugLog.getLogFile()?.let { file ->
                                if (file.exists()) sb.append(file.readText()) else sb.append("(empty)")
                            }
                            val crashLogs = CrashHandler.getCrashLogs()
                            sb.append("\n\n=== CRASH LOGS (${crashLogs.size}) ===\n")
                            if (crashLogs.isEmpty()) sb.append("(none)")
                            else crashLogs.forEach { file ->
                                sb.append(file.readText()).append("\n---\n")
                            }
                            File(context.cacheDir, "hanzi_report.txt").apply {
                                writeText(sb.toString())
                            }
                        }
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            report
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Hanzi Report"))
                        CrashHandler.clearCrashLogs()
                        statusMessage = "Debug report ready to share."
                    } catch (error: Exception) {
                        statusMessage = "Couldn't export logs: ${error.localizedMessage ?: "Unknown error"}"
                    } finally {
                        isBusy = false
                    }
                }
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null)
            Spacer(modifier = Modifier.width(9.dp))
            Text("Export Logs & Debug Report")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { importListsLauncher.launch(arrayOf("application/json", "text/*")) },
                enabled = !isBusy,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(7.dp))
                Text("Import")
            }
            Button(
                onClick = {
                    val fileName = "HanziWriting-lists-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json"
                    exportListsLauncher.launch(fileName)
                },
                enabled = !isBusy,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(7.dp))
                Text("Export")
            }
        }
    }
}
