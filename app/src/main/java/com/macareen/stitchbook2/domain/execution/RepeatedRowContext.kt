package com.macareen.stitchbook2.domain.execution

/** Presentation context for imported repeats whose numbered rows are instruction leaves. */
data class RepeatedRowContext(val unitLabel: String, val current: Int, val first: Int, val last: Int) {
    companion object {
        private val heading = Regex("""^(row|round)\s+(\d+)(?:\s*\([^)]{1,24}\))?\s*:\s*\S""", RegexOption.IGNORE_CASE)

        fun of(guide: ValidatedGuideDefinition, address: ExecutionAddress): RepeatedRowContext? {
            val frame = address.ancestryFrames.lastOrNull() as? AncestryFrame.RepeatIteration ?: return null
            val repeat = guide.node(frame.containerNodeId) as? Repeat ?: return null
            if (repeat.children.size < 2 || address.instructionNodeId !in repeat.children) return null
            val labels = repeat.children.map { id ->
                val instruction = guide.node(id) as? Instruction ?: return null
                val match = heading.find(instruction.text.trim()) ?: return null
                val number = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
                match.groupValues[1].lowercase() to number
            }
            val unit = labels.first().first
            if (labels.any { it.first != unit }) return null
            val numbers = labels.map { it.second }
            if (numbers.zipWithNext().any { (a, b) -> b.toLong() != a.toLong() + 1 }) return null
            return RepeatedRowContext(unit, numbers[repeat.children.indexOf(address.instructionNodeId)], numbers.first(), numbers.last())
        }
    }
}
