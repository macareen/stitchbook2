package com.macareen.stitchbook2.domain.parsing

/** Conservative title evidence; never invent a title from an introductory paragraph. */
object PatternDocumentTitle {
    private val garment = Regex("""\b(cardigan|sweater|jumper|pullover|shawl|scarf|cowl|hat|beanie|socks?|mittens?|blanket|top|vest|dress|bolero|poncho|chaleco|chaqueta|jersey|bufanda|gorro|manta)\b""", RegexOption.IGNORE_CASE)
    private val instruction = Regex("""^(?:body|sleeves?|back|front|sizes?|materials?|gauge|needles?|hooks?|abbreviations?|row|round|cast|knit|purl|work|repeat|using|the|this|a|by|designed)\b""", RegexOption.IGNORE_CASE)
    private fun key(text: String) = text.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
    private fun candidate(text: String): Boolean = text.length in 4..80 &&
        text.split(' ').size in 2..10 && text.any(Char::isLetter) &&
        !text.any { it in ":@/\\.!?=\u00a9" } && !instruction.containsMatchIn(text) && !Regex("""\bby\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    fun find(document: ExtractedDocument): String? {
        val metadata = document.metadataTitle?.replace(Regex("""^Microsoft Word\s*[-\u2013\u2014]\s*""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\.(?:docx?|pdf)$""", RegexOption.IGNORE_CASE), "")?.trim()
        val opening = document.lines.filter { it.source.pageNumber == 1 }.take(8)
            .map { it.text.trim().replace(Regex("""\s+"""), " ") }
        // Visible wording wins over export-program metadata. Metadata alone may be a stale filename.
        return opening.firstOrNull { candidate(it) &&
            (garment.containsMatchIn(it) && it.split(' ').all { word -> word.firstOrNull()?.isUpperCase() == true || word in listOf("of", "the", "and", "for", "de") } || metadata?.let { meta -> key(meta).startsWith(key(it)) } == true) }
    }
}
