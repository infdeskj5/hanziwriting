package com.infdesk.hanziwriting.utils

sealed class IdsNode {
    data class Leaf(val char: Char) : IdsNode()
    data class Branch(val operator: Char, val children: List<IdsNode>) : IdsNode()
}

object IdsParser {
    fun parse(s: String): IdsNode? {
        if (s.isBlank()) return null
        var index = 0
        fun parseNode(): IdsNode {
            if (index >= s.length) return IdsNode.Leaf('？')
            val c = s[index++]
            val arity = when (c) {
                '⿰', '⿱', '⿴', '⿵', '⿶', '⿷', '⿸', '⿹', '⿺', '⿻' -> 2
                '⿲', '⿳' -> 3
                else -> 0
            }
            return if (arity == 0) {
                IdsNode.Leaf(c)
            } else {
                val children = (0 until arity).map { parseNode() }
                IdsNode.Branch(c, children)
            }
        }
        return parseNode()
    }

    fun countLeaves(node: IdsNode?): Int {
        return when (node) {
            is IdsNode.Leaf -> 1
            is IdsNode.Branch -> node.children.sumOf { countLeaves(it) }
            null -> 0
        }
    }
}
