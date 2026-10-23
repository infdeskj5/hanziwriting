package com.infdesk.hanziwriting.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infdesk.hanziwriting.data.HanziDao

data class IdsBuildResult(
    val root: IdsNode?,
    val leafRank: Map<Int, Int>,
    val atomicLeaves: List<IdsNode.Leaf>
)

class IdsTreeBuilder(private val dao: HanziDao) {
    private val gson = Gson()
    private var leafCounter = 0

    suspend fun build(char: String, numStrokes: Int): IdsBuildResult {
        leafCounter = 0
        val root = buildNode(char, 0)
        val ranks = mutableMapOf<Int, Int>()
        if (root != null) {
            computeRanks(char, root, (0 until numStrokes).toList(), ranks, intArrayOf(0))
        }
        val atomicLeaves = mutableListOf<IdsNode.Leaf>()
        if (root != null) collectLeaves(root, atomicLeaves)
        return IdsBuildResult(root, ranks, atomicLeaves.filter { it.index >= 0 })
    }

    private suspend fun buildNode(char: String, depth: Int): IdsNode {
        val firstChar = char.firstOrNull() ?: '？'
        if (depth > 5) return IdsNode.Leaf(firstChar, if (char == "？") -1 else leafCounter++)
        val charData = dao.getCharacterExact(char)
        val decomp = charData?.decomposition ?: ""
        if (isAtomic(decomp, char)) {
            val idx = if (char == "？" || char.isBlank()) -1 else leafCounter++
            return IdsNode.Leaf(firstChar, idx)
        }
        val tree = IdsParser.parse(decomp) ?: run {
            return IdsNode.Leaf(firstChar, leafCounter++)
        }
        return expand(tree, char, depth)
    }

    private suspend fun expand(node: IdsNode, sourceChar: String, depth: Int): IdsNode {
        return when (node) {
            is IdsNode.Leaf -> {
                if (node.char == '？' || node.char == ' ') IdsNode.Leaf(node.char, -1)
                else buildNode(node.char.toString(), depth + 1)
            }
            is IdsNode.Branch -> {
                val newChildren = node.children.map { expand(it, sourceChar, depth) }
                IdsNode.Branch(node.operator, newChildren, sourceChar)
            }
        }
    }

    private fun isAtomic(decomp: String, char: String): Boolean {
        if (decomp.isBlank()) return true
        if (decomp == char) return true
        val ops = setOf('⿰','⿱','⿲','⿳','⿴','⿵','⿶','⿷','⿸','⿹','⿺','⿻')
        return decomp.first() !in ops
    }

    private suspend fun computeRanks(
        char: String, subtreeRoot: IdsNode, parentStrokes: List<Int>,
        ranks: MutableMap<Int, Int>, counter: IntArray
    ) {
        if (subtreeRoot is IdsNode.Leaf) {
            if (subtreeRoot.index >= 0 && !ranks.containsKey(subtreeRoot.index)) {
                ranks[subtreeRoot.index] = counter[0]; counter[0]++
            }
            return
        }
        val charData = dao.getCharacterExact(char)
        val matches = parseMatches(charData?.matchesJson ?: "")

        val nodeStrokes = LinkedHashMap<IdsNode, MutableList<Int>>()
        for (k in parentStrokes.indices) {
            if (k < matches.size) {
                val path = matches[k]
                if (path != null && path.isNotEmpty()) {
                    val node = resolveInTree(subtreeRoot, path)
                    if (node != null) nodeStrokes.getOrPut(node) { mutableListOf() }.add(parentStrokes[k])
                }
            }
        }
        val ordered = nodeStrokes.entries.sortedBy { it.value.firstOrNull() ?: Int.MAX_VALUE }
        for ((node, strokes) in ordered) {
            when (node) {
                is IdsNode.Leaf -> {
                    if (node.index >= 0 && !ranks.containsKey(node.index)) {
                        ranks[node.index] = counter[0]; counter[0]++
                    }
                }
                is IdsNode.Branch -> {
                    if (node.sourceChar.isNotBlank() && node.sourceChar != char) {
                        computeRanks(node.sourceChar, node, strokes, ranks, counter)
                    }
                }
            }
        }
        // fallback: rank any leaves the matches didn't cover
        val fallback = mutableListOf<IdsNode.Leaf>()
        collectLeaves(subtreeRoot, fallback)
        for (leaf in fallback) {
            if (leaf.index >= 0 && !ranks.containsKey(leaf.index)) {
                ranks[leaf.index] = counter[0]; counter[0]++
            }
        }
    }

    private fun resolveInTree(root: IdsNode, path: List<Int>): IdsNode? {
        var current = root
        for (idx in path) {
            if (current is IdsNode.Branch && idx >= 0 && idx < current.children.size) current = current.children[idx]
            else return null
        }
        return current
    }

    private fun collectLeaves(node: IdsNode, out: MutableList<IdsNode.Leaf>) {
        when (node) {
            is IdsNode.Leaf -> out.add(node)
            is IdsNode.Branch -> node.children.forEach { collectLeaves(it, out) }
        }
    }

    private fun parseMatches(json: String): List<List<Int>?> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<List<Number>?>>() {}.type
            val raw: List<List<Number>?> = gson.fromJson(json, type)
            raw.map { list -> list?.map { it.toInt() } }
        } catch (e: Exception) { emptyList() }
    }
}
