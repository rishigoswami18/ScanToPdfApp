package com.hrishipvt.scantopdf.ai

object TextChunker {

    fun split(text: String, maxLen: Int = 3000): List<String> {
        val clean = text
            .replace(Regex("[^\\x20-\\x7E\\n]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.length <= maxLen) return listOf(clean)

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < clean.length) {
            val end = minOf(start + maxLen, clean.length)
            chunks.add(clean.substring(start, end))
            start = end
        }
        return chunks
    }
}
