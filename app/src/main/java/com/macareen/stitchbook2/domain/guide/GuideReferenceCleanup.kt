package com.macareen.stitchbook2.domain.guide

import com.macareen.stitchbook2.domain.execution.NodeId

data class ReferenceCleanup(val draft: GuideDraft, val movedTexts: List<String>, val glossaryTexts: List<String>) {
    fun appendTo(notes: String?): String = listOfNotNull(notes?.takeIf { it.isNotBlank() },
        movedTexts.filterNot { it in glossaryTexts }.takeIf { it.isNotEmpty() }?.joinToString("\n\n", "## Reference\n"),
        glossaryTexts.takeIf { it.isNotEmpty() }?.joinToString("\n", "## Glossary\n")).joinToString("\n\n")
}

/** Explicit draft-only review. Published definitions and execution history are never modified. */
object GuideReferenceCleanup {
    private val referenceHeading = Regex("""^(Before you start|Abbreviations?|Glossary|Size guide|Size chart|Description|Measurements?|Materials?|Gauge|Techniques)$""", RegexOption.IGNORE_CASE)
    private val definition = Regex("""^([\p{L}][\p{L}\d-]{0,14})\s*[:=]\s*.+""", RegexOption.DOT_MATCHES_ALL)
    private val action = Regex("""^(?:rows?|rounds?|passes?|cast on|chain|ch\s+\d|knit|purl|work|repeat|rep|bind off|cast off|pick up|join|fasten off|turn)\b""", RegexOption.IGNORE_CASE)
    private val referencePrefix = Regex("""^(?:Abbreviations?|Glossary|Materials?|Gauge|Measurements?|Designer|Sizes?|Description)\s*:|^(?:The sizes?\b|The measurements?\b|Before beginning your project\b)""", RegexOption.IGNORE_CASE)
    private val glossaryPrefix = Regex("""^(?:Abbreviations?|Glossary)\b|^(?:RS|WS)\s+(?:right|wrong)\s+side\b""", RegexOption.IGNORE_CASE)
    fun preview(draft: GuideDraft): ReferenceCleanup {
        val byId = draft.nodes.associateBy { it.id }
        val kept = mutableListOf<DraftNode>()
        val texts = mutableListOf<String>()
        val glossary = mutableListOf<String>()
        fun visit(id: NodeId, inReference: Boolean): NodeId? {
            val node = requireNotNull(byId[id])
            val context = inReference || node.title?.let(referenceHeading::matches) == true
            if (node.type == DraftNodeType.INSTRUCTION) {
                val text = node.instructionText.orEmpty()
                val term = definition.matches(text) && !referencePrefix.containsMatchIn(text) && !action.containsMatchIn(text) && !Regex("^(?:Review needed|Note|Tip|Warning|Caution):", RegexOption.IGNORE_CASE).containsMatchIn(text)
                if (term || glossaryPrefix.containsMatchIn(text) || referencePrefix.containsMatchIn(text) || context && !action.containsMatchIn(text) && !Regex("^(?:Review needed|Note|Tip|Warning|Caution):", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                    texts += text
                    if (term || glossaryPrefix.containsMatchIn(text)) glossary += text
                    return null
                }
            }
            val children = node.children.mapNotNull { visit(it, context) }
            if (node.children.isNotEmpty() && children.isEmpty()) return null
            kept += node.copy(children = children)
            return id
        }
        val roots = draft.rootNodeIds.mapNotNull { visit(it, false) }
        return ReferenceCleanup(draft.copy(rootNodeIds = roots, nodes = kept), texts, glossary)
    }
}
