package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.utils.IdsNode

@Composable
fun StructureView(
    root: IdsNode?,
    leafRank: Map<Int, Int>,
    selectedCount: Int,
    modifier: Modifier = Modifier
) {
    if (root == null) return
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        IdsNodeView(root, leafRank, selectedCount, Modifier.fillMaxSize())
    }
}

@Composable
private fun IdsNodeView(node: IdsNode, leafRank: Map<Int, Int>, selectedCount: Int, modifier: Modifier) {
    when (node) {
        is IdsNode.Leaf -> LeafBox(node, leafRank[node.index] ?: -1, selectedCount, modifier)
        is IdsNode.Branch -> {
            when (node.operator) {
                '⿰', '⿲' -> Row(modifier) {
                    node.children.forEach { IdsNodeView(it, leafRank, selectedCount, Modifier.weight(1f).fillMaxHeight()) }
                }
                '⿱', '⿳' -> Column(modifier) {
                    node.children.forEach { IdsNodeView(it, leafRank, selectedCount, Modifier.fillMaxWidth().weight(1f)) }
                }
                // FIX: enclosures & overlap no longer overlap ugly — stacked with inner inset
                else -> Column(modifier) {
                    node.children.forEachIndexed { i, child ->
                        val childMod = if (i == 0) {
                            Modifier.fillMaxWidth().weight(1f)
                        } else {
                            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp)
                        }
                        IdsNodeView(child, leafRank, selectedCount, childMod)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeafBox(leaf: IdsNode.Leaf, rank: Int, selectedCount: Int, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline
    val state = when {
        rank < 0 -> 0
        rank < selectedCount -> 2
        rank == selectedCount -> 1
        else -> 0
    }
    // Filled polygons now use the SAME colors as the option buttons
    val fill = when (state) {
        2 -> primary
        1 -> primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val border = if (state >= 1) primary else outline
    Box(
        modifier = modifier
            .padding(3.dp)
            .border(2.dp, border, RoundedCornerShape(8.dp))
            .background(fill, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (state == 2) {
            Text(leaf.char.toString(), style = MaterialTheme.typography.titleMedium, color = onPrimary)
        }
    }
}
