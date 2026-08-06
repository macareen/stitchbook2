package com.macareen.stitchbook2.domain.model

data class StashItem(
    val id: String,
    val name: String,
    val category: StashCategory,
    val brand: String?,
    val colorway: String?,
    val dyeLot: String?,
    val weightCategory: String?,
    val fiberContent: String?,
    val quantity: Double,
    val unitLabel: String,
    val yardagePerUnit: Double?,
    val notes: String?,
    /** Where this item is physically kept -- meaningful for any category, unlike the yarn-only fields above. */
    val storageLocation: String?,
    /** Care/washing instructions -- meaningful for any category (a fabric or notion can need care instructions too, not just yarn). */
    val careInstructions: String?,
    /** External Ravelry yarn database ID (PRODUCT_SPEC.md 6.7) -- yarn-only, like [colorway]/[dyeLot]/[weightCategory]/[fiberContent]/[yardagePerUnit]. */
    val ravelryYarnId: String?,
    val purchaseSource: String?,
    val purchasePrice: Double?,
    /** ISO-8601 date-only string ("yyyy-MM-dd"), e.g. "2024-03-15" -- a purchase date has no time-of-day meaning (ARCHITECTURE.md 9's "local dates for date-only concepts"). */
    val purchaseDate: String?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class StashCategory(val storageValue: String) {
    YARN("YARN"),
    NEEDLES_HOOKS("NEEDLES_HOOKS"),
    NOTIONS("NOTIONS"),
    MATERIALS("MATERIALS");

    companion object {
        fun fromStorageValue(value: String): StashCategory? =
            entries.firstOrNull { it.storageValue == value }
    }
}

fun normalizedStashItemName(value: String): String? {
    return value.trim().takeIf { it.isNotEmpty() }
}
