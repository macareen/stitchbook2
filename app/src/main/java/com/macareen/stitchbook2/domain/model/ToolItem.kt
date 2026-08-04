package com.macareen.stitchbook2.domain.model

/**
 * One physically countable tool component -- a single needle pair, one
 * interchangeable tip, one cable, a bag of stitch markers, and so on. A
 * component may optionally belong to a [ToolSet] via [setId]; set membership
 * is a reference to this underlying inventory, never a second stock count
 * (see PRODUCT_SPEC.md 6.8 and ARCHITECTURE.md 9).
 *
 * Sizing and length are stored canonically in millimeters so they can be
 * sorted, compared, and displayed in any unit without relying on ambiguous
 * size labels; [sizeLabel] is an optional convenience label (for example
 * "US 7" or "H/8") the user may also record, but it is never the source of
 * truth for a value.
 */
data class ToolItem(
    val id: String,
    val name: String,
    val category: ToolCategory,
    val brand: String?,
    val material: String?,
    val sizeMetricMm: Double?,
    val sizeLabel: String?,
    val lengthMm: Double?,
    val statedCableLengthMm: Double?,
    val cableLengthDefinition: String?,
    val approximateAssembledLengthMm: Double?,
    val connectorFamily: String?,
    val compatibilityNotes: String?,
    val quantity: Int,
    val storageLocation: String?,
    val notes: String?,
    val setId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ToolCategory(val storageValue: String) {
    STRAIGHT_NEEDLES("STRAIGHT_NEEDLES"),
    CIRCULAR_NEEDLES("CIRCULAR_NEEDLES"),
    DPN_SET("DPN_SET"),
    INTERCHANGEABLE_TIP("INTERCHANGEABLE_TIP"),
    INTERCHANGEABLE_CABLE("INTERCHANGEABLE_CABLE"),
    CROCHET_HOOK("CROCHET_HOOK"),
    TUNISIAN_HOOK("TUNISIAN_HOOK"),
    LOOM("LOOM"),
    CABLE_NEEDLE("CABLE_NEEDLE"),
    STITCH_MARKER("STITCH_MARKER"),
    CONNECTOR("CONNECTOR"),
    END_STOPPER("END_STOPPER"),
    TIGHTENING_KEY("TIGHTENING_KEY"),
    OTHER_NOTION("OTHER_NOTION");

    companion object {
        fun fromStorageValue(value: String): ToolCategory? =
            entries.firstOrNull { it.storageValue == value }
    }
}

/**
 * A named grouping of [ToolItem] components, such as a complete commercial
 * interchangeable set. A set has no quantity or size of its own -- its
 * availability and composition are always derived from the components whose
 * [ToolItem.setId] points at it.
 */
data class ToolSet(
    val id: String,
    val name: String,
    val brand: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
