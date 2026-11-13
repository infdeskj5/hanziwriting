package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.infdesk.hanziwriting.data.AppDatabase

private data class StrokeRule(
    val title: String,
    val description: String,
    val example: String,
    val note: String
)

private data class StrokeExample(
    val medians: List<List<List<Double>>>,
    val strokes: List<String>
)

private val strokeRules = listOf(
    StrokeRule(
        "Top to bottom",
        "Write the upper strokes before the strokes below them.",
        "三",
        "Follow the natural downward direction of the character."
    ),
    StrokeRule(
        "Left to right",
        "When parts sit side by side, begin on the left and continue to the right.",
        "川",
        "This keeps the components in a consistent writing order."
    ),
    StrokeRule(
        "Horizontal before vertical",
        "When a horizontal and a vertical stroke cross, write the horizontal one first.",
        "十",
        "The horizontal stroke is laid down before the crossing vertical stroke."
    ),
    StrokeRule(
        "Left-falling before right-falling",
        "For paired falling strokes, write the left-falling stroke before the right-falling one.",
        "人",
        "The two strokes form one balanced movement."
    ),
    StrokeRule(
        "Outside before inside",
        "For an enclosing shape, begin with its outside structure before writing the inside component.",
        "同",
        "Build the outer frame first, then add what belongs inside it."
    ),
    StrokeRule(
        "Inside before closing",
        "Write the inner stroke before finishing the enclosing shape.",
        "日",
        "The final stroke closes the box only after the inside is complete."
    ),
    StrokeRule(
        "Middle before sides",
        "In balanced characters, write the central stroke before the strokes on either side.",
        "小",
        "The centre establishes the structure; the side strokes follow."
    ),
    StrokeRule(
        "Some dots come last",
        "A detached dot is sometimes written after the main body of the character.",
        "犬",
        "This is a common exception to the general top-to-bottom tendency."
    ),
    StrokeRule(
        "Bottom horizontal last",
        "When a vertical stroke crosses several horizontal strokes, the bottom horizontal is usually written last.",
        "王",
        "Finish the lower supporting line after the strokes above it."
    ),
    StrokeRule(
        "Spanning horizontal last",
        "A middle horizontal stroke that cuts across the character is often written after the other strokes.",
        "女",
        "The long crossbar completes the structure rather than starting it."
    ),
    StrokeRule(
        "Walking radical last",
        "In characters with 辶 or 廴, write the main component first and the walking radical afterward.",
        "建",
        "The enclosing movement is added after the carried component."
    ),
    StrokeRule(
        "Bottom enclosure last",
        "For a character enclosed from below, write the inside and upper structure before the bottom enclosure.",
        "凶",
        "The lower frame is the final stroke group."
    ),
    StrokeRule(
        "Crossing vertical last",
        "A long vertical stroke that passes through the rest of a character can be written after the strokes it crosses.",
        "中",
        "The central line is added after the enclosing horizontal structure."
    ),
    StrokeRule(
        "Crossing diagonals",
        "When two diagonal strokes cross or connect, the right-to-left diagonal is written before the left-to-right diagonal.",
        "交",
        "This is a more specific rule for connected diagonal strokes."
    )
)

private fun parseStrokeExample(rawJson: String): StrokeExample? = runCatching {
    val root = JsonParser.parseString(rawJson).asJsonObject
    val gson = Gson()
    val mediansType = object : TypeToken<List<List<List<Double>>>>() {}.type
    val strokesType = object : TypeToken<List<String>>() {}.type
    val medians: List<List<List<Double>>> = gson.fromJson(root.get("medians"), mediansType)
        ?: emptyList()
    val strokes: List<String> = gson.fromJson(root.get("strokes"), strokesType)
        ?: emptyList()
    StrokeExample(medians, strokes).takeIf { it.medians.isNotEmpty() }
}.getOrNull()

@Composable
fun StrokeRulesCarousel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).hanziDao() }
    val preferences = remember {
        context.getSharedPreferences("hanzi_home_rules", android.content.Context.MODE_PRIVATE)
    }
    var selectedIndex by remember {
        mutableIntStateOf(preferences.getInt("selected_rule_index", 0).coerceIn(strokeRules.indices))
    }
    var example by remember(selectedIndex) { mutableStateOf<StrokeExample?>(null) }
    val rule = strokeRules[selectedIndex]

    LaunchedEffect(selectedIndex) {
        preferences.edit().putInt("selected_rule_index", selectedIndex).apply()
        example = null
        val graphics = dao.getGraphics(rule.example)
        if (graphics != null) example = parseStrokeExample(graphics.rawJson)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "STROKE ORDER  ·  ${selectedIndex + 1}/${strokeRules.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        "Writing guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(rule.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        rule.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        rule.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (example != null) {
                        AnimationCanvas(
                            medians = example!!.medians,
                            strokes = example!!.strokes,
                            modifier = Modifier.size(100.dp),
                            autoPlay = true,
                            loop = true,
                            showGuide = false,
                            useSvgFill = true,
                            frameDelayMs = 16,
                            onComplete = {}
                        )
                    } else {
                        // Keep the example area visually quiet until its SVG is ready.
                        // Do not flash the complete glyph before the stroke animation starts.
                        Spacer(modifier = Modifier.size(100.dp))
                    }
                    Text(
                        rule.example,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) },
                    enabled = selectedIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous stroke rule")
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    strokeRules.indices.forEach { index ->
                        val active = index == selectedIndex
                        Surface(
                            modifier = Modifier.size(if (active) 8.dp else 6.dp),
                            shape = RoundedCornerShape(50),
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ) {}
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { selectedIndex = (selectedIndex + 1).coerceAtMost(strokeRules.lastIndex) },
                    enabled = selectedIndex < strokeRules.lastIndex
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next stroke rule")
                }
            }
        }
    }
}
