package com.macareen.stitchbook2.domain.ravelry

import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem

/** An item Ravelry has that this device already pulled before, but with different details now. */
data class RavelryChange<T>(val local: T, val updated: T)

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
    val unchangedToolCount: Int
) {
    val hasNothingToDo: Boolean
        get() = newStash.isEmpty() && changedStash.isEmpty() && newTools.isEmpty() && changedTools.isEmpty()
}

object RavelryImportPlanner {

    const val STASH_ID_PREFIX = "ravelry-stash-"
    const val TOOL_ID_PREFIX = "ravelry-needle-"
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

    /** "4.0 mm", "4mm", or "4" -> 4.0; anything else (US or letter sizes) -> null. */
    internal fun millimetres(text: String?): Double? {
        val match = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(mm)?\s*$""", RegexOption.IGNORE_CASE).find(text ?: return null)
        return match?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.5..50.0 }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
