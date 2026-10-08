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
    val updatedAt: Long,
    /** Manufacturer's stated weight of one full unit (skein/ball), in grams. */
    val weightPerUnitGrams: Double? = null,
    /**
     * A *measured* (scale) weight in grams of what's left across all units,
     * recorded by the user for partial skeins. Null means "not weighed";
     * [quantity] still tracks full and fractional units either way.
     */
    val remainingWeightGrams: Double? = null
)

/**
 * Estimated remaining length in yards, derived from the measured
 * [StashItem.remainingWeightGrams] and the stated yards-per-gram ratio of a
 * full unit. Always an *estimate* (dye, moisture and ply vary), rounded to
 * one decimal place; null when any input is missing or the unit weight is 0.
 */
fun StashItem.estimatedRemainingYards(): Double? {
    val remaining = remainingWeightGrams ?: return null
    val yardsPerUnit = yardagePerUnit ?: return null
    val gramsPerUnit = weightPerUnitGrams ?: return null
    if (gramsPerUnit <= 0.0 || remaining < 0.0) return null
    return Math.round(remaining / gramsPerUnit * yardsPerUnit * 10.0) / 10.0
}

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
