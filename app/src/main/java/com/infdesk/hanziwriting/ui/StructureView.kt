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
                // Enclosures: outer full-size, inner smaller + offset to the open side (no overlap!)
                else -> {
                    val (innerAlign, innerFraction) = when (node.operator) {
                        '⿴' -> Alignment.Center to 0.55f        // full surround (国)
                        '⿵' -> Alignment.BottomCenter to 0.65f   // top surround (同)
                        '⿶' -> Alignment.TopCenter to 0.65f      // bottom surround (凶)
                        '⿷' -> Alignment.CenterEnd to 0.65f      // left surround (区)
                        '⿸' -> Alignment.BottomEnd to 0.65f      // top-left (床)
                        '⿹' -> Alignment.BottomStart to 0.65f    // top-right (句)
                        '⿺' -> Alignment.TopEnd to 0.55f         // bottom-left (匕) - smaller inner
                        else -> Alignment.Center to 0.6f
                    }
                    Box(modifier) {
                        // Outer polygon: full size
                        if (node.children.isNotEmpty()) {
                            IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxSize())
                        }
                        // Inner polygon: smaller and offset to the open side
                        if (node.children.size > 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(innerFraction)
                                    .align(innerAlign)
                            ) {
                                IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize())
                            }
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
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline
    val state = when {
        rank < 0 -> 0
        rank < selectedCount -> 2
        rank == selectedCount -> 1
        else -> 0
    }
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
