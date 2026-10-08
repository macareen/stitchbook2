package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.parsing.StructuredGuide
import com.macareen.stitchbook2.domain.parsing.StructuredGuideMapper
import com.macareen.stitchbook2.domain.repository.GuideRepository

/**
 * Creates a new Guide whose Draft holds a validated [StructuredGuide], the
 * same way a hand-written draft is saved. It never publishes: the person
 * reviews the draft in the Draft editor first.
 */
class CreateGuideFromStructuredGuideUseCase(
    private val guideRepository: GuideRepository,
    private val newNodeId: () -> String
) {
    suspend operator fun invoke(projectId: String, guideName: String, guide: StructuredGuide): GuideId {
        val mapped = StructuredGuideMapper.toDraftNodes(guide, newNodeId)
        val created = guideRepository.createGuide(projectId, guideName)
        val emptyDraft = requireNotNull(guideRepository.loadDraft(created.id)) {
            "Newly created guide ${created.id.value} is missing its draft."
        }
        guideRepository.saveDraft(emptyDraft.copy(rootNodeIds = mapped.rootNodeIds, nodes = mapped.nodes))
        return created.id
    }
}
