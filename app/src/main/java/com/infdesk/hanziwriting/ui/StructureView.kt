package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        IdsNodeView(
            node = root,
            leafRank = leafRank,
            selectedCount = selectedCount,
            modifier = Modifier.fillMaxSize()
        )
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
        is IdsNode.Leaf -> {
            LeafBox(
                leaf = node,
                rank = leafRank[node.index] ?: -1,
                selectedCount = selectedCount,
                modifier = modifier,
                frame = frame
            )
        }

        is IdsNode.Branch -> {
            when (node.operator) {
                '⿰', '⿲' -> Row(modifier) {
                    node.children.forEach { child ->
                        IdsNodeView(
                            node = child,
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                '⿱', '⿳' -> Column(modifier) {
                    node.children.forEach { child ->
                        IdsNodeView(
                            node = child,
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }

                '⿴' -> Box(modifier) {
                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier.fillMaxSize(),
                            frame = true
                        )
                    }

                    if (node.children.size > 1) {
                        Box(
                            Modifier
                                .fillMaxSize(0.6f)
                                .align(Alignment.Center)
                        ) {
                            IdsNodeView(
                                node = node.children[1],
                                leafRank = leafRank,
                                selectedCount = selectedCount,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                '⿵' -> Column(modifier) {
                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.35f)
                        )
                    }

                    if (node.children.size > 1) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(0.65f)
                                .padding(horizontal = 12.dp)
                        ) {
                            IdsNodeView(
                                node = node.children[1],
                                leafRank = leafRank,
                                selectedCount = selectedCount,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                '⿶' -> Column(modifier) {
                    if (node.children.size > 1) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(0.65f)
                                .padding(horizontal = 12.dp)
                        ) {
                            IdsNodeView(
                                node = node.children[1],
                                leafRank = leafRank,
                                selectedCount = selectedCount,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.35f)
                        )
                    }
                }

                '⿷' -> Row(modifier) {
                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.35f)
                        )
                    }

                    if (node.children.size > 1) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .weight(0.65f)
                                .padding(vertical = 12.dp)
                        ) {
                            IdsNodeView(
                                node = node.children[1],
                                leafRank = leafRank,
                                selectedCount = selectedCount,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                '⿸' -> Box(modifier) {
                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxSize(0.5f)
                                .align(Alignment.TopStart)
                        )
                    }

                    if (node.children.size > 1) {
                        IdsNodeView(
                            node = node.children[1],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxSize(0.5f)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                '⿹' -> Box(modifier) {
                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxSize(0.5f)
                                .align(Alignment.TopEnd)
                        )
                    }

                    if (node.children.size > 1) {
                        IdsNodeView(
                            node = node.children[1],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxSize(0.5f)
                                .align(Alignment.BottomStart)
                        )
                    }
                }

                '⿺' -> Box(modifier) {
                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxSize(0.55f)
                                .align(Alignment.BottomStart)
                        )
                    }

                    if (node.children.size > 1) {
                        IdsNodeView(
                            node = node.children[1],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier
                                .fillMaxSize(0.45f)
                                .align(Alignment.TopEnd)
                        )
                    }
                }

                else -> Box(modifier) {
                    if (node.children.isNotEmpty()) {
                        IdsNodeView(
                            node = node.children[0],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier.fillMaxSize(),
                            frame = true
                        )
                    }

                    if (node.children.size > 1) {
                        IdsNodeView(
                            node = node.children[1],
                            leafRank = leafRank,
                            selectedCount = selectedCount,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeafBox(
    leaf: IdsNode.Leaf,
    rank: Int,
    selectedCount: Int,
    modifier: Modifier,
    frame: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outlineVariant

    val state = when {
        rank < 0 -> 0
        rank < selectedCount -> 2
        rank == selectedCount -> 1
        else -> 0
    }

    val fill = when {
        state == 2 -> if (frame) primary.copy(alpha = 0.28f) else primary
        state == 1 -> if (frame) primary.copy(alpha = 0.12f) else primary.copy(alpha = 0.16f)
        else -> Color.Transparent
    }

    val borderColor = when {
        state == 2 -> primary
        state == 1 -> primary.copy(alpha = 0.75f)
        else -> outline
    }

    val borderWidth = if (frame) 3.dp else 2.dp

    val textColor = when {
        state == 2 -> if (frame) primary else onPrimary
        state == 1 -> primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier
            .padding(3.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .background(fill, RoundedCornerShape(8.dp)),
        contentAlignment = if (frame) Alignment.TopCenter else Alignment.Center
    ) {
        Text(
            leaf.char.toString(),
            style = if (frame) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = textColor
        )
    }
}
