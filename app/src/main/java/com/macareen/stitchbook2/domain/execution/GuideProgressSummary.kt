package com.macareen.stitchbook2.domain.execution

/** The stitch count a step states, such as "(24 sts)", "— 24 stitches" or "Cast on 66 sts". */
object StitchCount {

    private val BRACKETED = Regex("""[(\[]\s*(\d+)\s*(?:sts?|stitches)\.?\s*[)\]]""", RegexOption.IGNORE_CASE)
    private val TRAILING = Regex("""[–—:=-]\s*(\d+)\s*(?:sts?|stitches)\.?\s*(?:\(p\.\d+\))?\s*$""", RegexOption.IGNORE_CASE)
    private val CAST_ON = Regex("""\bcast\s+on\s+(\d+)\s*(?:sts?|stitches)\b""", RegexOption.IGNORE_CASE)

    /** The last count the text states, or null when it states none. */
    fun from(text: String): Int? =
        (BRACKETED.findAll(text).lastOrNull() ?: TRAILING.find(text) ?: CAST_ON.find(text))
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
}

/** What a progress percentage is measured in. */
enum class ProgressBasis {
    /** Every step states its stitch count. */
    STITCHES,

    /** Some steps state a count; the others are taken to have the count stated before them. */
    STITCHES_ESTIMATED,

    /** No step states a count, so each step counts once. */
    STEPS
}

data class GuideProgressSummary(
    val completedSteps: Int,
    val totalSteps: Int,
    val fraction: Double,
    val basis: ProgressBasis
) {
    val percent: Int get() = (fraction * 100).toInt().coerceIn(0, 100)

    companion object {
        /**
         * How far through the guide [completedAddresses] are, weighted by
         * stitches. A step that states no count is taken to have the count
         * last stated before it (the stitches still on the needle), or the
         * first one stated after it near the start; that makes the result an
         * estimate, which [basis] says. Without any counts every step weighs
         * the same.
         */
        fun of(guide: ValidatedGuideDefinition, completedAddresses: Set<ExecutionAddress>): GuideProgressSummary {
            val records = GuideTraversal(guide).occurrenceRecords().toList()
            val stated = records.map { StitchCount.from(it.instruction.text) }
            val firstStated = stated.firstOrNull { it != null }
            var carried = firstStated
            val weights = stated.map { count ->
                if (count != null) carried = count
                (count ?: carried ?: 1).toDouble()
            }
            val done = records.indices.filter { records[it].address in completedAddresses }
            val total = weights.sum()
            val basis = when {
                firstStated == null -> ProgressBasis.STEPS
                stated.all { it != null } -> ProgressBasis.STITCHES
                else -> ProgressBasis.STITCHES_ESTIMATED
            }
            return GuideProgressSummary(
                completedSteps = done.size,
                totalSteps = records.size,
                fraction = if (total > 0) done.sumOf { weights[it] } / total else 0.0,
                basis = basis
            )
        }
    }
}

enum class OverviewStatus { DONE, CURRENT, TO_DO }

/**
 * One line of a guide's overview: a section heading, a span of rows, a
 * repeat, or a single step, with how far it has got and where to jump to
 * work on it.
 */
data class OverviewEntry(
    val nodeId: NodeId,
    val depth: Int,
    val kind: Kind,
    val text: String,
    val status: OverviewStatus,
    val completedSteps: Int,
    val totalSteps: Int,
    /** The first step still to do inside this entry, or its first step when it's all done. */
    val jumpTarget: ExecutionAddress
) {
    enum class Kind { SECTION, ROWS, REPEAT, STEP }

    companion object {
        /** The guide as a list a knitter can scan, in order, each entry at its nesting [depth]. */
        fun overviewOf(
            guide: ValidatedGuideDefinition,
            completedAddresses: Set<ExecutionAddress>,
            currentAddress: ExecutionAddress?
        ): List<OverviewEntry> {
            val records = GuideTraversal(guide).occurrenceRecords().toList()
            val entries = mutableListOf<OverviewEntry>()

            fun visit(nodeId: NodeId, depth: Int) {
                val node = guide.node(nodeId) ?: return
                val inside = records.filter { nodeId in it.nodePath || it.instruction.id == nodeId }
                if (inside.isEmpty()) return
                val completed = inside.count { it.address in completedAddresses }
                val status = when {
                    completed == inside.size -> OverviewStatus.DONE
                    currentAddress != null && inside.any { it.address == currentAddress } -> OverviewStatus.CURRENT
                    else -> OverviewStatus.TO_DO
                }
                val target = (inside.firstOrNull { it.address !in completedAddresses } ?: inside.first()).address
                val (kind, text) = when (node) {
                    is Section -> Kind.SECTION to node.title
                    is Range -> Kind.ROWS to rangeLabel(node, guide)
                    is Repeat -> Kind.REPEAT to (node.label?.let { "$it × ${node.count}" } ?: "Repeat × ${node.count}")
                    is Instruction -> Kind.STEP to node.text
                    else -> return
                }
                entries += OverviewEntry(nodeId, depth, kind, text, status, completed, inside.size, target)
                // A span of rows shows as one line; sections and repeats list what's inside.
                if (node is Section || node is Repeat) {
                    (node as GuideContainer).children.forEach { visit(it, depth + 1) }
                }
            }

            guide.definition.rootNodeIds.forEach { visit(it, 0) }
            return entries
        }

        private fun rangeLabel(range: Range, guide: ValidatedGuideDefinition): String {
            val unit = range.unitLabel.replaceFirstChar { it.uppercase() }
            val span = if (range.startInclusive == range.endInclusive) {
                "$unit ${range.startInclusive}"
            } else {
                "${unit}s ${range.startInclusive}–${range.endInclusive}"
            }
            val first = range.children.firstNotNullOfOrNull { guide.node(it) as? Instruction }?.text
            return if (first != null) "$span: $first" else span
        }
    }
}
