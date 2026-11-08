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
private fun IdsNodeView(
    node: IdsNode,
    leafRank: Map<Int, Int>,
    selectedCount: Int,
    modifier: Modifier,
    frame: Boolean = false
) {
    when (node) {
        is IdsNode.Leaf -> LeafBox(node, leafRank[node.index] ?: -1, selectedCount, modifier, frame)
        is IdsNode.Branch -> {
            when (node.operator) {
                '⿰', '⿲' -> Row(modifier) {
                    node.children.forEach { IdsNodeView(it, leafRank, selectedCount, Modifier.weight(1f).fillMaxHeight()) }
                }
                '⿱', '⿳' -> Column(modifier) {
                    node.children.forEach { IdsNodeView(it, leafRank, selectedCount, Modifier.fillMaxWidth().weight(1f)) }
                }
                '⿴' -> Box(modifier) { // full surround: outer as frame, inner centered
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxSize(), frame = true)
                    if (node.children.size > 1) {
                        Box(Modifier.fillMaxSize(0.6f).align(Alignment.Center)) {
                            IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize())
                        }
                    }
                }
                '⿵' -> Column(modifier) { // top surround
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxWidth().weight(0.35f))
                    if (node.children.size > 1) {
                        Box(Modifier.fillMaxWidth().weight(0.65f).padding(horizontal = 12.dp)) {
                            IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize())
                        }
                    }
                }
                '⿶' -> Column(modifier) { // bottom surround
                    if (node.children.size > 1) {
                        Box(Modifier.fillMaxWidth().weight(0.65f).padding(horizontal = 12.dp)) {
                            IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize())
                        }
                    }
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxWidth().weight(0.35f))
                }
                '⿷' -> Row(modifier) { // left surround
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxHeight().weight(0.35f))
                    if (node.children.size > 1) {
                        Box(Modifier.fillMaxHeight().weight(0.65f).padding(vertical = 12.dp)) {
                            IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize())
                        }
                    }
                }
                '⿸' -> Box(modifier) { // top-left: diagonal quadrants, no overlap
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxSize(0.5f).align(Alignment.TopStart))
                    if (node.children.size > 1) IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize(0.5f).align(Alignment.BottomEnd))
                }
                '⿹' -> Box(modifier) { // top-right
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxSize(0.5f).align(Alignment.TopEnd))
                    if (node.children.size > 1) IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize(0.5f).align(Alignment.BottomStart))
                }
                '⿺' -> Box(modifier) { // bottom-left (匕): outer shrunk, inner top-right
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxSize(0.55f).align(Alignment.BottomStart))
                    if (node.children.size > 1) IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize(0.45f).align(Alignment.TopEnd))
                }
                else -> Box(modifier) { // ⿻ overlap: alpha-differentiated
                    if (node.children.isNotEmpty()) IdsNodeView(node.children[0], leafRank, selectedCount, Modifier.fillMaxSize(), frame = true)
                    if (node.children.size > 1) IdsNodeView(node.children[1], leafRank, selectedCount, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun LeafBox(leaf: IdsNode.Leaf, rank: Int, selectedCount: Int, modifier: Modifier, frame: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline
    val state = when {
        rank < 0 -> 0
        rank < selectedCount -> 2
        rank == selectedCount -> 1
        else -> 0
    }
    val fill = when {
        state == 2 -> if (frame) primary.copy(alpha = 0.25f) else primary
        state == 1 -> if (frame) primary.copy(alpha = 0.10f) else primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val border = if (state >= 1) primary else outline
    val borderWidth = if (frame) 3.dp else 2.dp
    Box(
        modifier = modifier
            .padding(3.dp)
            .border(borderWidth, border, RoundedCornerShape(8.dp))
            .background(fill, RoundedCornerShape(8.dp)),
        contentAlignment = if (frame) Alignment.TopCenter else Alignment.Center
    ) {
        if (state == 2) {
            Text(
                leaf.char.toString(),
                style = if (frame) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                color = if (frame) primary else onPrimary
            )
        }
    }
}
