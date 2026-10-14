package com.macareen.stitchbook2.feature.focus

/** Presentation only: separate clear actions without rewriting a guide or its execution. */
internal object FocusInstructionLayout {
    private val action = Regex(
        """^(?:(?:then|and)\s+)?(?:marker\s+\d+|\d+\s+(?:sts?|stitches)\b|knit|purl|slip|make|increase|decrease|cast\s+on|bind\s+off|pick\s+up|yarn\s+over|place|remove|move|transfer|turn|join|attach|fasten|weave|work|repeat|continue|chain|skip|draw|insert|wrap|pass|lift|hold|slide|single\s+crochet|double\s+crochet|half\s+double\s+crochet|treble\s+crochet|m1[lr]?|kfb|pfb|ssk|ssp|k\d+tog|p\d+tog|k\d*|p\d*|sl\d*|yo|pm|sm|rm|ch\d*|sc\d*|dc\d*|hdc\d*|tr\d*|dtr\d*|tss\d*|tks\d*|tps\d*|tfs\d*|trs\d*)\b""",
        RegexOption.IGNORE_CASE
    )

    fun actionLines(text: String): List<String> = text.lineSequence().flatMap { line -> splitLine(line).asSequence() }.toList()

    data class Block(val text: String, val children: List<Block> = emptyList(), val suffix: String? = null)

    /** Balanced inline groups remain visible scopes, never extra execution steps. */
    fun blocks(text: String): List<Block> = blocks(text, 0)

    private fun blocks(text: String, depth: Int): List<Block> {
        val original = text.trim()
        if (original.isEmpty()) return emptyList()
        if (depth >= 8 || groupRanges(original) == null) return listOf(Block(original))
        return splitLine(original, splitNewlines = true).flatMap { part ->
            val groups = groupRanges(part).orEmpty()
            val expandable = groups.firstNotNullOfOrNull { (start, end) ->
                val body = part.substring(start + 1, end).trim()
                val children = blocks(body, depth + 1)
                val startsWithAction = action.containsMatchIn(body) || children.firstOrNull()?.children?.isNotEmpty() == true
                if (startsWithAction && (children.size > 1 || children.any { it.children.isNotEmpty() })) {
                    Triple(start, end, children)
                } else null
            }
            if (expandable == null) listOf(Block(part)) else {
                val (start, end, children) = expandable
                val tail = part.substring(end + 1).trim()
                val tailBlocks = if (tail.isEmpty()) emptyList() else blocks(tail, depth + 1)
                if (tailBlocks.any { it.children.isNotEmpty() }) {
                    listOf(Block(part.substring(0, start + 1).trim(), children, part[end].toString())) + tailBlocks
                } else {
                    listOf(Block(part.substring(0, start + 1).trim(), children, part.substring(end).trim()))
                }
            }
        }
    }

    private fun escaped(text: String, index: Int): Boolean {
        var preceding = index - 1
        while (preceding >= 0 && text[preceding] == '\\') preceding--
        return (index - preceding - 1) % 2 == 1
    }

    private fun groupRanges(text: String): List<Pair<Int, Int>>? {
        val stack = ArrayDeque<Pair<Char, Int>>()
        val groups = mutableListOf<Pair<Int, Int>>()
        var quoted = false
        text.forEachIndexed { index, char ->
            if (char == '"' && !escaped(text, index)) quoted = !quoted
            if (quoted || char == '"') return@forEachIndexed
            val star = char == '*' && text.getOrNull(index - 1) != '*' && text.getOrNull(index + 1) != '*'
            if (char == '(' || char == '[' || (star && stack.lastOrNull()?.first != '*')) {
                stack.addLast(char to index)
            } else if (char == ')' || char == ']' || star) {
                val opening = stack.removeLastOrNull() ?: return null
                val expected = when (char) { ')' -> '('; ']' -> '['; else -> '*' }
                if (opening.first != expected) return null
                if (stack.isEmpty()) groups += opening.second to index
            }
        }
        return groups.takeIf { !quoted && stack.isEmpty() }
    }

    private fun splitLine(line: String, splitNewlines: Boolean = false): List<String> {
        val original = line.trim()
        if (original.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        val brackets = ArrayDeque<Char>()
        var quoted = false
        var starred = false
        var start = 0
        for (index in original.indices) {
            val char = original[index]
            if (char == '"' && !escaped(original, index)) { quoted = !quoted; continue }
            if (quoted) continue
            if (char == '*' && original.getOrNull(index - 1) != '*' && original.getOrNull(index + 1) != '*') {
                starred = !starred
                continue
            }
            if (starred) continue
            when (char) {
                '(', '[' -> brackets.addLast(char)
                ')', ']' -> {
                    if (brackets.isEmpty() || brackets.removeLast() != if (char == ')') '(' else '[') return listOf(original)
                }
            }
            val newline = splitNewlines && char in listOf('\n', '\r')
            if (brackets.isNotEmpty() || (!newline && char !in listOf(',', ';', '.'))) continue
            if (char == '.' && original.getOrNull(index + 1)?.isWhitespace() != true) continue
            val next = original.substring(index + 1).trimStart()
            if (!newline && !action.containsMatchIn(next)) continue
            val end = if (char == '.') index + 1 else index
            original.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(parts::add)
            start = index + 1
        }
        if (quoted || starred || brackets.isNotEmpty()) return listOf(original)
        original.substring(start).trim().takeIf { it.isNotEmpty() }?.let(parts::add)
        return parts.ifEmpty { listOf(original) }
    }
}
