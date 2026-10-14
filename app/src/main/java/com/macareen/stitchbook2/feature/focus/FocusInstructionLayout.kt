package com.macareen.stitchbook2.feature.focus

/** Presentation only: separate clear actions without rewriting a guide or its execution. */
internal object FocusInstructionLayout {
    private val action = Regex(
        """^(?:(?:then|and)\s+)?(?:marker\s+\d+|\d+\s+(?:sts?|stitches)\b|knit|purl|slip|make|increase|decrease|cast\s+on|bind\s+off|pick\s+up|yarn\s+over|place|remove|move|transfer|turn|join|attach|fasten|weave|work|repeat|continue|chain|skip|draw|insert|wrap|pass|lift|hold|slide|single\s+crochet|double\s+crochet|half\s+double\s+crochet|treble\s+crochet|m1[lr]?|kfb|pfb|ssk|ssp|k\d+tog|p\d+tog|k\d*|p\d*|sl\d*|yo|pm|sm|rm|ch\d*|sc\d*|dc\d*|hdc\d*|tr\d*|dtr\d*|tss\d*|tks\d*|tps\d*|tfs\d*|trs\d*)\b""",
        RegexOption.IGNORE_CASE
    )

    fun actionLines(text: String): List<String> = text.lineSequence().flatMap { line -> splitLine(line).asSequence() }.toList()

    private fun splitLine(line: String): List<String> {
        val original = line.trim()
        if (original.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        val brackets = ArrayDeque<Char>()
        var quoted = false
        var starred = false
        var start = 0
        for (index in original.indices) {
            val char = original[index]
            if (char == '"') { quoted = !quoted; continue }
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
            if (brackets.isNotEmpty() || char !in listOf(',', ';', '.')) continue
            if (original.getOrNull(index + 1)?.isWhitespace() != true) continue
            val next = original.substring(index + 1).trimStart()
            if (!action.containsMatchIn(next)) continue
            val end = if (char == '.') index + 1 else index
            original.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(parts::add)
            start = index + 1
        }
        if (quoted || starred || brackets.isNotEmpty()) return listOf(original)
        original.substring(start).trim().takeIf { it.isNotEmpty() }?.let(parts::add)
        return parts.ifEmpty { listOf(original) }
    }
}
