package com.macareen.stitchbook2.domain.library

import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.ProjectStatus

/** Recorded completions only: linking a pattern or writing a date does not finish a project. */
fun completedProjectCount(patternId: String, projects: List<Project>, links: List<ProjectPatternLink>): Int {
    val linked = links.filter { it.libraryItemId == patternId }.map { it.projectId }.toSet()
    val completed = projects.filter { it.status == ProjectStatus.COMPLETED }.map { it.id }.toSet()
    return linked.intersect(completed).size
}
