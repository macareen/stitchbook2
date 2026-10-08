package com.macareen.stitchbook2.domain.ravelry

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem

/** An item Ravelry has that this device already pulled before, but with different details now. */
data class RavelryChange<T>(val local: T, val updated: T)

/** New, changed, and unchanged records of one kind. */
data class RavelryKindPlan<T>(val new: List<T> = emptyList(), val changed: List<RavelryChange<T>> = emptyList(), val unchanged: Int = 0) {
    val isEmpty: Boolean get() = new.isEmpty() && changed.isEmpty()

    fun toSave(includeChanges: Boolean): List<T> = new + if (includeChanges) changed.map { it.updated } else emptyList()
}

/**
 * What a pull from Ravelry would do, worked out before anything is saved so
 * the person can review it. New records are added; records pulled before
 * (matched by their Ravelry id) are only updated if the person agrees, and
 * then only Ravelry's fields change -- local notes, purchase details, care
 * instructions, and weighed remainders are kept.
 */
data class RavelryImportPlan(
    val newStash: List<StashItem>,
    val changedStash: List<RavelryChange<StashItem>>,
    val unchangedStashCount: Int,
    val newTools: List<ToolItem>,
    val changedTools: List<RavelryChange<ToolItem>>,
    val unchangedToolCount: Int,
    val projects: RavelryKindPlan<Project> = RavelryKindPlan(),
    val patterns: RavelryKindPlan<LibraryItem> = RavelryKindPlan()
) {
    val hasNothingToDo: Boolean
        get() = newStash.isEmpty() && changedStash.isEmpty() && newTools.isEmpty() && changedTools.isEmpty() &&
            projects.isEmpty && patterns.isEmpty

    val hasNew: Boolean
        get() = newStash.isNotEmpty() || newTools.isNotEmpty() || projects.new.isNotEmpty() || patterns.new.isNotEmpty()

    val changeCount: Int
        get() = changedStash.size + changedTools.size + projects.changed.size + patterns.changed.size
}

object RavelryImportPlanner {

    const val STASH_ID_PREFIX = "ravelry-stash-"
    const val TOOL_ID_PREFIX = "ravelry-needle-"
    const val PROJECT_ID_PREFIX = "ravelry-project-"
    const val VOLUME_ID_PREFIX = "ravelry-volume-"
    private const val UNIT_SKEINS = "skeins"

    fun plan(
        stash: List<RavelryStashEntry>,
        needles: List<RavelryNeedle>,
        localStash: List<StashItem>,
        localTools: List<ToolItem>,
        now: Long
    ): RavelryImportPlan {
        val stashById = localStash.associateBy { it.id }
        val newStash = mutableListOf<StashItem>()
        val changedStash = mutableListOf<RavelryChange<StashItem>>()
        var unchangedStash = 0
        for (entry in stash) {
            val local = stashById[STASH_ID_PREFIX + entry.id]
            if (local == null) {
                newStash += stashItem(entry, now)
            } else {
                val updated = mergeStash(local, entry)
                if (updated == local) unchangedStash++ else changedStash += RavelryChange(local, updated.copy(updatedAt = now))
            }
        }

        val toolsById = localTools.associateBy { it.id }
        val newTools = mutableListOf<ToolItem>()
        val changedTools = mutableListOf<RavelryChange<ToolItem>>()
        var unchangedTools = 0
        for (needle in needles) {
            val local = toolsById[TOOL_ID_PREFIX + needle.id]
            if (local == null) {
                newTools += toolItem(needle, now)
            } else {
                val updated = mergeTool(local, needle)
                if (updated == local) unchangedTools++ else changedTools += RavelryChange(local, updated.copy(updatedAt = now))
            }
        }

        return RavelryImportPlan(newStash, changedStash, unchangedStash, newTools, changedTools, unchangedTools)
    }

    internal fun stashItem(entry: RavelryStashEntry, now: Long): StashItem = mergeStash(
        StashItem(
            id = STASH_ID_PREFIX + entry.id,
            name = "",
            category = StashCategory.YARN,
            brand = null,
            colorway = null,
            dyeLot = null,
            weightCategory = null,
            fiberContent = null,
            quantity = 0.0,
            unitLabel = UNIT_SKEINS,
            yardagePerUnit = null,
            notes = null,
            storageLocation = null,
            careInstructions = null,
            ravelryYarnId = null,
            purchaseSource = null,
            purchasePrice = null,
            purchaseDate = null,
            createdAt = now,
            updatedAt = now
        ),
        entry
    )

    /** Ravelry's fields onto [local]; a field Ravelry leaves empty keeps the local value. */
    private fun mergeStash(local: StashItem, entry: RavelryStashEntry): StashItem = local.copy(
        name = entry.yarnName.clean() ?: entry.name.clean() ?: local.name.ifBlank { "Ravelry stash ${entry.id}" },
        brand = entry.companyName.clean() ?: local.brand,
        colorway = entry.colorway.clean() ?: local.colorway,
        dyeLot = entry.dyeLot.clean() ?: local.dyeLot,
        weightCategory = entry.weightName.clean() ?: local.weightCategory,
        quantity = entry.skeins?.takeIf { it >= 0 } ?: local.quantity.takeIf { it > 0 } ?: 1.0,
        yardagePerUnit = entry.yardsPerSkein?.takeIf { it > 0 } ?: local.yardagePerUnit,
        weightPerUnitGrams = entry.gramsPerSkein?.takeIf { it > 0 } ?: local.weightPerUnitGrams,
        storageLocation = entry.location.clean() ?: local.storageLocation,
        ravelryYarnId = entry.yarnId?.toString() ?: local.ravelryYarnId
    )

    internal fun toolItem(needle: RavelryNeedle, now: Long): ToolItem = mergeTool(
        ToolItem(
            id = TOOL_ID_PREFIX + needle.id,
            name = "",
            category = ToolCategory.OTHER_NOTION,
            brand = null,
            material = null,
            sizeMetricMm = null,
            sizeLabel = null,
            lengthMm = null,
            statedCableLengthMm = null,
            cableLengthDefinition = null,
            approximateAssembledLengthMm = null,
            connectorFamily = null,
            compatibilityNotes = null,
            quantity = 1,
            storageLocation = null,
            notes = null,
            setId = null,
            createdAt = now,
            updatedAt = now
        ),
        needle
    )

    private fun mergeTool(local: ToolItem, needle: RavelryNeedle): ToolItem {
        val size = needle.metricName.clean()
        val title = listOfNotNull(size, needle.typeName.clean(), needle.length.clean()).joinToString(" ")
        return local.copy(
            name = needle.name.clean() ?: title.ifBlank { null } ?: local.name.ifBlank { "Ravelry needle ${needle.id}" },
            category = categoryFor(needle.typeName) ?: local.category,
            sizeMetricMm = millimetres(size) ?: local.sizeMetricMm,
            sizeLabel = size ?: local.sizeLabel,
            // Notes start from Ravelry's comment (or its length, which is display text such as
            // "24 inch") and then belong to the person: a later pull never replaces them.
            notes = local.notes ?: needle.comment.clean() ?: needle.length.clean()?.let { "Length: $it" }
        )
    }

    internal fun categoryFor(typeName: String?): ToolCategory? {
        val type = typeName?.lowercase() ?: return null
        return when {
            "tunisian" in type -> ToolCategory.TUNISIAN_HOOK
            "hook" in type || "crochet" in type -> ToolCategory.CROCHET_HOOK
            "interchangeable" in type -> ToolCategory.INTERCHANGEABLE_TIP
            "circular" in type -> ToolCategory.CIRCULAR_NEEDLES
            "double" in type || "dpn" in type -> ToolCategory.DPN_SET
            "straight" in type || "single" in type -> ToolCategory.STRAIGHT_NEEDLES
            "cable" in type -> ToolCategory.CABLE_NEEDLE
            else -> null
        }
    }

    fun planProjects(remote: List<RavelryProject>, local: List<Project>, now: Long): RavelryKindPlan<Project> =
        planKind(remote.map { PROJECT_ID_PREFIX + it.id to it }, local.associateBy { it.id }, now,
            create = { id, project -> mergeProject(blankProject(id, now), project) },
            merge = ::mergeProject,
            touch = { item, time -> item.copy(updatedAt = time) })

    fun planPatterns(remote: List<RavelryVolume>, local: List<LibraryItem>, now: Long): RavelryKindPlan<LibraryItem> =
        planKind(remote.map { VOLUME_ID_PREFIX + it.id to it }, local.associateBy { it.id }, now,
            create = { id, volume -> mergeVolume(blankLibraryItem(id, now), volume) },
            merge = ::mergeVolume,
            touch = { item, time -> item.copy(updatedAt = time) })

    private fun <R, T> planKind(
        remote: List<Pair<String, R>>,
        localById: Map<String, T>,
        now: Long,
        create: (String, R) -> T,
        merge: (T, R) -> T,
        touch: (T, Long) -> T
    ): RavelryKindPlan<T> {
        val new = mutableListOf<T>()
        val changed = mutableListOf<RavelryChange<T>>()
        var unchanged = 0
        for ((id, record) in remote) {
            val existing = localById[id]
            if (existing == null) {
                new += create(id, record)
            } else {
                val updated = merge(existing, record)
                if (updated == existing) unchanged++ else changed += RavelryChange(existing, touch(updated, now))
            }
        }
        return RavelryKindPlan(new, changed, unchanged)
    }

    private fun blankProject(id: String, now: Long) = Project(
        id = id,
        name = "",
        craft = Craft.OTHER,
        projectType = ProjectType.OTHER,
        status = ProjectStatus.PLANNED,
        notes = null,
        createdAt = now,
        updatedAt = now
    )

    /** Ravelry's name, craft, status, and dates; the pattern name only fills an empty description. */
    private fun mergeProject(local: Project, project: RavelryProject): Project = local.copy(
        name = project.name.clean() ?: local.name.ifBlank { "Ravelry project ${project.id}" },
        craft = craftFor(project.craftName) ?: local.craft,
        status = statusFor(project.statusName) ?: local.status,
        startDate = isoDate(project.started) ?: local.startDate,
        targetDate = isoDate(project.finishBy) ?: local.targetDate,
        completedDate = isoDate(project.completed) ?: local.completedDate,
        description = local.description ?: project.patternName.clean()?.let { "Pattern: $it" }
    )

    private fun blankLibraryItem(id: String, now: Long) = LibraryItem(
        id = id,
        title = "",
        craft = Craft.OTHER,
        author = null,
        sourceUrl = null,
        tags = emptyList(),
        notes = null,
        bookmarked = false,
        createdAt = now,
        updatedAt = now
    )

    /** Ravelry volumes carry no craft, so a new entry starts as "Other" for the person to set. */
    private fun mergeVolume(local: LibraryItem, volume: RavelryVolume): LibraryItem = local.copy(
        title = volume.title.clean() ?: local.title.ifBlank { "Ravelry pattern ${volume.id}" },
        author = volume.authorName.clean() ?: local.author,
        ravelryPatternId = volume.patternId?.toString() ?: local.ravelryPatternId
    )

    internal fun craftFor(name: String?): Craft? {
        val craft = name?.lowercase() ?: return null
        return when {
            "tunisian" in craft -> Craft.TUNISIAN_CROCHET
            "loom" in craft -> Craft.LOOM_KNITTING
            "crochet" in craft -> Craft.CROCHET
            "knit" in craft -> Craft.KNITTING
            else -> Craft.OTHER
        }
    }

    internal fun statusFor(name: String?): ProjectStatus? {
        val status = name?.lowercase() ?: return null
        return when {
            "finish" in status -> ProjectStatus.COMPLETED
            "progress" in status -> ProjectStatus.ACTIVE
            "hibernat" in status -> ProjectStatus.PAUSED
            "frog" in status -> ProjectStatus.ABANDONED
            "plan" in status || "queue" in status -> ProjectStatus.PLANNED
            else -> null
        }
    }

    /** "2024/03/15" or "2024-03-15" (optionally followed by a time) -> "2024-03-15". */
    internal fun isoDate(text: String?): String? {
        val match = Regex("""^\s*(\d{4})[/-](\d{1,2})[/-](\d{1,2})""").find(text ?: return null) ?: return null
        val (year, month, day) = match.destructured
        return "%s-%02d-%02d".format(year, month.toInt(), day.toInt())
    }

    /** "4.0 mm", "4mm", or "4" -> 4.0; anything else (US or letter sizes) -> null. */
    internal fun millimetres(text: String?): Double? {
        val match = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(mm)?\s*$""", RegexOption.IGNORE_CASE).find(text ?: return null)
        return match?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.5..50.0 }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
