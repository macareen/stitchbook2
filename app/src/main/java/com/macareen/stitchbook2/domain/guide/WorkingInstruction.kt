package com.macareen.stitchbook2.domain.guide

/** Conservative display decomposition; stored instructions and execution identity are untouched. */
data class WorkingInstruction(val subtitle: String?, val action: String, val detail: String? = null, val castOnTarget: Int? = null) {
    companion object {
        private val row = Regex("""^(Row|Round|Round trip|Pass|Course|Panel|Motif)\s+(\d+)(\s*\([^)]*\))?\s*:\s*(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val castOn = Regex("""^(?:Cast\s+on|CO)\s+(\d+)\s+(?:sts?\.?|stitches?)\b""", RegexOption.IGNORE_CASE)
        fun of(text: String): WorkingInstruction {
            val body = text.trim()
            val marker = row.matchEntire(body)
            val action = marker?.groupValues?.get(4) ?: body
            val cast = castOn.find(action)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..1_000_000 }
            val subtitle = marker?.let { it.groupValues[1].replaceFirstChar(Char::uppercase) + " " + it.groupValues[2] + it.groupValues[3] }
                ?: if (cast != null) "Cast on" else null
            // Only split an explicit supporting note, never a shaping condition or arbitrary sentence.
            val note = Regex("""\s+(?:Note|Tip):\s+""", RegexOption.IGNORE_CASE).find(action)
            return if (note != null) WorkingInstruction(subtitle, action.substring(0, note.range.first), action.substring(note.range.last + 1), cast)
                else WorkingInstruction(subtitle, action, castOnTarget = cast)
        }
    }
}
