package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.data.HanziDao
import com.infdesk.hanziwriting.data.ListItem
import com.infdesk.hanziwriting.data.PracticeList
import com.infdesk.hanziwriting.data.StudyListBinding
import kotlinx.coroutines.launch

private const val SOURCE_HSK = "HSK"
private const val SOURCE_TOCFL = "TOCFL"
private val HSK_LEVELS = listOf("1", "2", "3", "4", "5", "6", "7-9")
private val TOCFL_LEVELS = listOf("N1", "N2", "1", "2", "3", "4", "5")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyListsSetting(dao: HanziDao) {
    val scope = rememberCoroutineScope()
    var bindings by remember { mutableStateOf<List<StudyListBinding>>(emptyList()) }
    var activeSource by remember { mutableStateOf<String?>(null) }
    var pendingLevels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSaving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refreshBindings() {
        val allLists = dao.getAllLists().map { it.listId }.toSet()
        val current = dao.getStudyListBindings()
        current.filterNot { it.listId in allLists }.forEach {
            dao.deleteStudyListBinding(it.source, it.level)
        }
        bindings = current.filter { it.listId in allLists }
    }

    LaunchedEffect(Unit) {
        runCatching { refreshBindings() }
    }

    fun showLevelPicker(source: String) {
        activeSource = source
        pendingLevels = bindings.filter { it.source == source }.map { it.level }.toSet()
        error = null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Vocabulary study lists", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "Add or remove complete curriculum levels from My Lists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showLevelPicker(SOURCE_HSK) },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) { Text("HSK 3.0") }
                OutlinedButton(
                    onClick = { showLevelPicker(SOURCE_TOCFL) },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) { Text("TOCFL") }
            }
            val selectedCount = bindings.size
            if (status != null) {
                Text(
                    status.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (selectedCount > 0) {
                Text(
                    "$selectedCount curriculum list(s) added",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    val source = activeSource
    if (source != null) {
        val levels = if (source == SOURCE_HSK) HSK_LEVELS else TOCFL_LEVELS
        val sourceTitle = if (source == SOURCE_HSK) "HSK 3.0" else "TOCFL"
        val levelName: (String) -> String = { level ->
            when {
                source == SOURCE_HSK && level == "7-9" -> "Level 7–9 (combined band)"
                source == SOURCE_TOCFL && level == "N1" -> "Novice 1"
                source == SOURCE_TOCFL && level == "N2" -> "Novice 2"
                else -> "Level $level"
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isSaving) activeSource = null },
            title = { Text("$sourceTitle levels") },
            text = {
                Column {
                    Text(
                        "Select the levels you want to add to My Lists. Unselecting a level removes its linked practice list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        levels.forEach { level ->
                            FilterChip(
                                selected = level in pendingLevels,
                                onClick = {
                                    pendingLevels = if (level in pendingLevels) {
                                        pendingLevels - level
                                    } else {
                                        pendingLevels + level
                                    }
                                },
                                enabled = !isSaving,
                                label = { Text(levelName(level)) }
                            )
                        }
                    }
                    if (isSaving) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Updating practice lists…", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSaving,
                    onClick = {
                        scope.launch {
                            isSaving = true
                            error = null
                            try {
                                val currentBindings = dao.getStudyListBindings()
                                    .filter { it.source == source }
                                    .associateBy { it.level }
                                val existingLists = dao.getAllLists().associateBy { it.listId }

                                // Remove only lists created and tracked by this curriculum setting.
                                (currentBindings.keys - pendingLevels).forEach { level ->
                                    val binding = currentBindings.getValue(level)
                                    dao.deleteListItems(binding.listId)
                                    dao.deletePracticeList(binding.listId)
                                    dao.deleteStudyListBinding(source, level)
                                }

                                // Repair stale bindings as well as creating newly selected levels.
                                pendingLevels.forEach { level ->
                                    val existing = currentBindings[level]
                                    if (existing != null && existing.listId in existingLists) return@forEach

                                    if (existing != null) dao.deleteStudyListBinding(source, level)
                                    val listName = if (source == SOURCE_HSK) {
                                        "HSK 3.0 · ${levelName(level)}"
                                    } else {
                                        "TOCFL · ${levelName(level)}"
                                    }
                                    val insertedId = dao.insertList(PracticeList(name = listName))
                                    check(insertedId > 0L) { "Couldn't create '$listName'." }
                                    val listId = insertedId.toInt()
                                    val words = dao.getVocabularyWords(source, level).distinct()
                                    check(words.isNotEmpty()) { "No vocabulary entries were found for ${levelName(level)}." }
                                    words.chunked(400).forEach { chunk ->
                                        dao.addCharactersToList(chunk.map { ListItem(listId, it) })
                                    }
                                    dao.upsertStudyListBinding(StudyListBinding(source, level, listId))
                                }

                                refreshBindings()
                                status = "Updated $sourceTitle study lists."
                                activeSource = null
                            } catch (failure: Exception) {
                                error = failure.localizedMessage ?: "Couldn't update the selected levels."
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                ) { Text(if (isSaving) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { if (!isSaving) activeSource = null }, enabled = !isSaving) {
                    Text("Cancel")
                }
            }
        )
    }
}
