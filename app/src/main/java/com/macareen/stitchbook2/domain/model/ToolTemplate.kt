package com.macareen.stitchbook2.domain.model

/** Bulk creation is scoped to categories with a meaningful numeric size --
 * see [ToolCategory.usesSizeFields]. Categories without a size (markers,
 * stoppers, notions...) already have adequate quantity support through the
 * single-item form, since there is no varying attribute to generate a size
 * range or list from. */
enum class BulkSizeInputMode { RANGE, CUSTOM_LIST }

/**
 * A saved, reusable preset of Bulk Create Tools' form fields (PRODUCT_SPEC.md
 * 6.8, "reusable user templates"). A template describes what to generate --
 * it is never itself live inventory, and applying one only pre-fills the
 * bulk-creation form; it never creates [ToolItem]s on its own (PRODUCT_SPEC.md
 * 6.8's "Templates describe what to create; they are not the authoritative
 * inventory after creation").
 *
 * Deliberately does not cover manufacturer-set templates (curated presets for
 * specific commercial products): that would require sourcing and maintaining
 * real manufacturer size/quantity data, a content problem rather than a
 * schema one, and is left for a separate future slice.
 */
data class ToolTemplate(
    val id: String,
    /** The template's own name, distinct from [setName] below. */
    val name: String,
    val category: ToolCategory,
    val brand: String?,
    val material: String?,
    val sizeInputMode: BulkSizeInputMode,
    val rangeStart: Double?,
    val rangeEnd: Double?,
    val rangeIncrement: Double?,
    /** Comma-separated, mirroring [BulkSizeInputMode.CUSTOM_LIST]'s own text field. */
    val customSizes: String?,
    val quantityPerSize: Int,
    val storageLocation: String?,
    val notes: String?,
    val createAsSet: Boolean,
    val setName: String?,
    val createdAt: Long,
    val updatedAt: Long
)

fun normalizedToolTemplateName(value: String): String? = value.trim().takeIf { it.isNotEmpty() }
