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
                '⿻' -> Box(modifier) {
                    node.children.forEach { IdsNodeView(it, leafRank, selectedCount, Modifier.fillMaxSize()) }
                }
                else -> Box(modifier) { // enclosures
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxSize())
                    if (node.children.size > 1) {
                        Box(Modifier.fillMaxSize().padding(14.dp)) {
                            node.children.drop(1).forEach { IdsNodeView(it, leafRank, selectedCount, Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeafBox(leaf: IdsNode.Leaf, rank: Int, selectedCount: Int, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val state = when {
        rank < 0 -> 0
        rank < selectedCount -> 2
        rank == selectedCount -> 1
        else -> 0
    }
    val fill = when (state) {
        2 -> primary.copy(alpha = 0.5f)
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
        // Request #2: show the radical inside the polygon once correctly selected
        if (state == 2) {
            Text(leaf.char.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
