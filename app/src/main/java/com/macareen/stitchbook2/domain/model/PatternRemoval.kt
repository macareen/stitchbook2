package com.macareen.stitchbook2.domain.model

data class PatternRemovalProject(val id: String, val name: String, val status: ProjectStatus)

/** A review of the index entry and its dependants, never permission to delete original files. */
data class PatternRemovalPreview(
    val item: LibraryItem,
    val guideIds: List<String>,
    val projects: List<PatternRemovalProject>
)

sealed interface PatternRemovalResult {
    data object Removed : PatternRemovalResult
    data object Missing : PatternRemovalResult
    data class Changed(val preview: PatternRemovalPreview) : PatternRemovalResult
}
