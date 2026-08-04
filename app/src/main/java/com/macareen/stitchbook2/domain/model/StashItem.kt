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
