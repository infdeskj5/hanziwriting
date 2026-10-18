package com.infdesk.hanziwriting.utils

object PinyinUtils {
    fun convertToToneMarks(pinyin: String): String {
        return pinyin.split(" ").joinToString(" ") { convertSyllable(it) }
    }

    private fun convertSyllable(syllable: String): String {
        val tone = syllable.lastOrNull { it in '1'..'5' } ?: return syllable
        val base = syllable.dropLast(1)
        if (tone == '5') return base // Neutral tone

        val vowelIndices = base.mapIndexedNotNull { index, char ->
            if (char in "aeiouüv") index else null
        }
        if (vowelIndices.isEmpty()) return base + tone

        val targetIndex = when {
            base.contains('a') -> base.indexOf('a')
            base.contains('e') -> base.indexOf('e')
            base.contains("ou") -> base.indexOf('o')
            else -> vowelIndices.last() // Last vowel gets the mark
        }

        val vowelChar = base[targetIndex]
        val toneChar = getToneChar(vowelChar, tone.digitToInt())
        return base.substring(0, targetIndex) + toneChar + base.substring(targetIndex + 1)
    }

    private fun getToneChar(vowel: Char, tone: Int): Char {
        val map = mapOf(
            'a' to "aāáǎà", 'e' to "eēéěè", 'i' to "iīíǐì",
            'o' to "oōóǒò", 'u' to "uūúǔù", 'v' to "üǖǘǚǜ", 'ü' to "üǖǘǚǜ"
        )
        return map[vowel]?.getOrNull(tone) ?: vowel
    }
}
