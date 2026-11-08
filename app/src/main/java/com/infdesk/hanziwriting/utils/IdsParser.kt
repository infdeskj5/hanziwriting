package com.infdesk.hanziwriting.utils

sealed class IdsNode {
    data class Leaf(val char: Char, val index: Int) : IdsNode()
    data class Branch(val operator: Char, val children: List<IdsNode>) : IdsNode()
}

object IdsParser {
    private val IDS_SYMBOLS = setOf('⿰','⿱','⿲','⿳','⿴','⿵','⿶','⿷','⿸','⿹','⿺','⿻')

    private fun getArity(c: Char): Int = when (c) {
        '⿲', '⿳' -> 3
        in IDS_SYMBOLS -> 2
        else -> 0
    }

    fun parse(s: String): IdsNode? {
        if (s.isBlank()) return null
        var index = 0
        var leafCount = 0
        fun build(): IdsNode {
            if (index >= s.length) return IdsNode.Leaf('？', -1)
            val c = s[index++]
            val arity = getArity(c)
            return if (arity > 0) {
                val children = (0 until arity).map { build() }
                IdsNode.Branch(c, children)
            } else {
                if (c == '？' || c == ' ') IdsNode.Leaf(c, -1)
                else IdsNode.Leaf(c, leafCount++)
            }
        }
        return build()
    }

    fun getLeavesWithIndex(node: IdsNode?): List<IdsNode.Leaf> {
        val leaves = mutableListOf<IdsNode.Leaf>()
        fun traverse(n: IdsNode) {
            when (n) {
                is IdsNode.Leaf -> if (n.index >= 0 && n.char != '？' && n.char != ' ') leaves.add(n)
                is IdsNode.Branch -> n.children.forEach { traverse(it) }
            }
        }
        if (node != null) traverse(node)
        return leaves
    }

    fun resolvePath(node: IdsNode?, path: List<Int>): Int {
        if (node == null) return -1
        var current = node
        for (i in path) {
            if (current is IdsNode.Branch && i >= 0 && i < current.children.size) current = current.children[i]
            else return -1
        }
        return if (current is IdsNode.Leaf) current.index else -1
    }
}
