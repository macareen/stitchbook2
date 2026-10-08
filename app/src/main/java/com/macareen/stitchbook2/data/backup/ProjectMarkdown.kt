package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.GuideBackupGraph
import com.macareen.stitchbook2.domain.backup.BackupSnapshot
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.roundQuantity
import com.macareen.stitchbook2.domain.model.sortedForDisplay
import java.time.Instant
import java.time.ZoneId

/**
 * A human-readable Markdown summary of one project, for reading outside the
 * app. It is a one-way export: the JSON export is the restorable format.
 *
 * Photos are listed by the display name captured at attach time, never by
 * `content://` URI -- a URI means nothing outside this device. Values that
 * depend on an assumption (stitches per row, yards per unit) are labelled
 * as estimates, per AGENTS.md.
 */
fun projectMarkdown(project: Project, snapshot: BackupSnapshot, now: Long): String = buildString {
    val zone = ZoneId.systemDefault()
    line("# ${project.name.trim()}")
    line()
    line("_Exported from Stitchbook on ${Instant.ofEpochMilli(now).atZone(zone).toLocalDate()}._")
    line()

    line("## Details")
    line()
    field("Craft", humanize(project.craft.storageValue))
    val typeLabel = project.customTypeLabel?.takeIf { project.projectType == ProjectType.OTHER && it.isNotBlank() }
        ?: humanize(project.projectType.storageValue)
    field("Type", typeLabel)
    field("Status", humanize(project.status.storageValue))
    field("Construction", project.constructionMethod)
    field("Started", project.startDate)
    field("Target", project.targetDate)
    field("Completed", project.completedDate)
    line()
    project.description?.takeIf { it.isNotBlank() }?.let {
        line("### Description")
        line()
        line(it.trim())
        line()
    }
    project.notes?.takeIf { it.isNotBlank() }?.let {
        line("### Notes")
        line()
        line(it.trim())
        line()
    }

    val milestones = snapshot.milestones.orEmpty().filter { it.projectId == project.id }
        .sortedWith(compareBy({ it.position }, { it.createdAt }))
    if (milestones.isNotEmpty()) {
        line("## Milestones")
        line()
        milestones.forEachIndexed { index, milestone ->
            val date = milestone.reachedDate?.let { " — reached $it" } ?: " — not yet reached"
            line("${index + 1}. ${milestone.title.trim()}$date")
            milestone.notes?.takeIf { it.isNotBlank() }?.let { line("   ${it.trim()}") }
        }
        line()
    }

    val sessions = snapshot.sessions.orEmpty().filter { it.projectId == project.id }
    if (sessions.isNotEmpty()) {
        line("## Crafting time")
        line()
        field("Sessions", sessions.size.toString())
        field("Time worked", markdownDuration(sessions.sumOf { it.workedMillis(now) }))
        val rowSessions = sessions.filter { (it.rowsCompleted ?: 0) > 0 }
        if (rowSessions.isNotEmpty()) {
            field("Rows/rounds recorded", rowSessions.sumOf { it.rowsCompleted ?: 0 }.toString())
        }
        val stitchSessions = rowSessions.filter { (it.stitchesPerRow ?: 0) > 0 }
        if (stitchSessions.isNotEmpty()) {
            val stitches = stitchSessions.sumOf { (it.rowsCompleted ?: 0) * (it.stitchesPerRow ?: 0) }
            field("Stitches (estimate)", "about $stitches, from your stitches-per-row figures")
        }
        line()
    }

    val stashById = snapshot.stashItems.orEmpty().associateBy { it.id }
    val allocations = snapshot.yarnAllocations.orEmpty().filter { it.projectId == project.id }
    if (allocations.isNotEmpty()) {
        line("## Yarn and materials")
        line()
        allocations.forEach { allocation ->
            val item = stashById[allocation.stashItemId]
            val name = item?.let { listOfNotNull(it.brand, it.name, it.colorway).joinToString(" · ") }
                ?: "(stash item not in this export)"
            val unit = item?.unitLabel.orEmpty()
            val parts = mutableListOf(
                "${formatQuantity(allocation.quantityReserved)} $unit reserved".trim(),
                "${formatQuantity(allocation.quantityUsed)} $unit used".trim()
            )
            item?.yardagePerUnit?.takeIf { it > 0.0 && allocation.quantityUsed > 0.0 }?.let { yards ->
                parts += "about ${formatQuantity(roundQuantity(yards * allocation.quantityUsed))} yds used (estimate)"
            }
            line("- $name: ${parts.joinToString(", ")}")
            allocation.notes?.takeIf { it.isNotBlank() }?.let { line("  ${it.trim()}") }
        }
        line()
    }

    val libraryById = snapshot.libraryItems.orEmpty().associateBy { it.id }
    val patterns = snapshot.patternLinks.orEmpty().filter { it.projectId == project.id }
    if (patterns.isNotEmpty()) {
        line("## Patterns")
        line()
        patterns.forEach { link ->
            val pattern = libraryById[link.libraryItemId]
            if (pattern == null) {
                line("- (pattern not in this export)")
            } else {
                val author = pattern.author?.takeIf { it.isNotBlank() }?.let { " by ${it.trim()}" }.orEmpty()
                val file = pattern.pdfFileName?.let { " — file: $it" }.orEmpty()
                line("- ${pattern.title.trim()}$author$file")
                pattern.sourceUrl?.takeIf { it.isNotBlank() }?.let { line("  <${it.trim()}>") }
            }
        }
        line()
    }

    snapshot.guideGraph()?.let { graph -> guidesSection(project.id, graph) }

    val photos = snapshot.photos.orEmpty().filter { it.projectId == project.id }.sortedBy { it.createdAt }
    if (photos.isNotEmpty()) {
        line("## Photos")
        line()
        line("Photos stay in your own storage; they are listed here by file name.")
        line()
        photos.forEach { photo ->
            val name = photo.displayName?.takeIf { it.isNotBlank() } ?: "(unnamed photo)"
            val details = listOfNotNull(photo.takenDate, photo.caption?.takeIf { it.isNotBlank() }?.trim())
            line("- $name" + if (details.isEmpty()) "" else " — ${details.joinToString(", ")}")
        }
        line()
    }

    val journal = snapshot.journalEntries.orEmpty().filter { it.projectId == project.id }.sortedForDisplay()
    if (journal.isNotEmpty()) {
        line("## Journal")
        line()
        journal.forEach { entry ->
            val title = entry.title?.takeIf { it.isNotBlank() }?.let { " — ${it.trim()}" }.orEmpty()
            line("### ${entry.entryDate}$title")
            line()
            line(entry.body.trim())
            line()
        }
    }
}.trimEnd() + "\n"

/**
 * The guides this project knits from, written out as plain nested steps: the
 * latest published revision, or the draft when nothing is published yet, plus
 * how many steps this project has done.
 */
private fun StringBuilder.guidesSection(projectId: String, graph: GuideBackupGraph) {
    val usedIds = graph.projectGuides.filter { it.projectId == projectId }.map { it.guideId }.toSet()
    val guides = graph.guides.filter { it.projectId == projectId || it.id in usedIds }.sortedBy { it.name.lowercase() }
    if (guides.isEmpty()) return
    line("## Guides")
    line()
    guides.forEach { guide ->
        val size = guide.sizeLabel?.takeIf { it.isNotBlank() }?.let { " (size ${it.trim()})" }.orEmpty()
        line("### ${guide.name.trim()}$size")
        line()
        val revision = graph.revisions.filter { it.guideId == guide.id }.maxByOrNull { it.revisionNumber }
        val nodes = revision?.nodes ?: graph.drafts.firstOrNull { it.guideId == guide.id }?.nodes.orEmpty()
        if (revision == null) {
            line("Draft, not yet published.")
            line()
        }
        graph.executions
            .filter { it.guideId == guide.id && it.projectId == projectId }
            .maxByOrNull { it.updatedAt }
            ?.let { progress ->
                val state = if (progress.completedAt != null) "finished" else "in progress"
                line("Progress: ${progress.completedOccurrences.size} steps done, $state.")
                line()
            }
        val children = nodes.groupBy { it.parentNodeId }.mapValues { (_, list) -> list.sortedBy { it.childOrder } }
        fun write(parentId: String?, depth: Int) {
            children[parentId].orEmpty().forEach { node ->
                val indent = "  ".repeat(depth)
                val text = when (node.type) {
                    "SECTION" -> "**${node.title.orEmpty().trim()}**"
                    "RANGE" -> {
                        val unit = node.rangeUnitLabel.orEmpty().replaceFirstChar { it.uppercase() }
                        if (node.rangeStartInclusive == node.rangeEndInclusive) "$unit ${node.rangeStartInclusive}:"
                        else "${unit}s ${node.rangeStartInclusive}–${node.rangeEndInclusive}:"
                    }
                    "REPEAT" -> listOfNotNull(node.repeatLabel?.takeIf { it.isNotBlank() }?.trim(), "repeat ${node.repeatCount}×").joinToString(", ") + ":"
                    else -> node.instructionText.orEmpty().trim()
                }
                line("$indent- $text")
                write(node.nodeId, depth + 1)
            }
        }
        write(null, 0)
        line()
    }
}

private fun StringBuilder.line(text: String = "") {
    append(text).append('\n')
}

private fun StringBuilder.field(label: String, value: String?) {
    if (!value.isNullOrBlank()) line("- **$label:** ${value.trim()}")
}

/** "TUNISIAN_CROCHET" -> "Tunisian crochet". */
private fun humanize(storageValue: String): String =
    storageValue.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** "1 h 05 min" or "12 min", whole minutes rounded down; matches the in-app duration text. */
private fun markdownDuration(millis: Long): String {
    val totalMinutes = millis.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h %02d min".format(minutes) else "$minutes min"
}

private fun formatQuantity(value: Double): String {
    val rounded = roundQuantity(value)
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}
