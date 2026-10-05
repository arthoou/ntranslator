package com.arthou.ntranslator.translator

object GoogleLineSegments {
    private const val APPROX_SINGLE_LINE_CHARS = 30
    private const val DERIVED_INDEX_OFFSET = 1_000_000_000
    private const val INDEX_MULTIPLIER = 100

    fun split(text: String): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) {
            return emptyList()
        }

        val lines = mutableListOf<String>()
        val current = StringBuilder()

        for (word in words) {
            val nextLength = if (current.isEmpty()) word.length else current.length + 1 + word.length
            if (current.isNotEmpty() && nextLength > APPROX_SINGLE_LINE_CHARS) {
                lines += current.toString()
                current.clear()
            }

            if (current.isNotEmpty()) {
                current.append(' ')
            }
            current.append(word)
        }

        if (current.isNotEmpty()) {
            lines += current.toString()
        }

        return lines
    }

    fun index(baseIndex: Int, segmentIndex: Int): Int {
        return DERIVED_INDEX_OFFSET + (baseIndex * INDEX_MULTIPLIER) + segmentIndex
    }

    fun isDerivedIndex(index: Int): Boolean {
        return index >= DERIVED_INDEX_OFFSET
    }

    fun baseIndex(index: Int): Int {
        if (!isDerivedIndex(index)) {
            return index
        }

        return (index - DERIVED_INDEX_OFFSET) / INDEX_MULTIPLIER
    }

    fun segmentIndex(index: Int): Int {
        if (!isDerivedIndex(index)) {
            return 0
        }

        return (index - DERIVED_INDEX_OFFSET) % INDEX_MULTIPLIER
    }
}
