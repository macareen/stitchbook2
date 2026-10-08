package com.macareen.stitchbook2.domain.model

data class Project(
    val id: String,
    val name: String,
    val craft: Craft,
    val projectType: ProjectType,
    val status: ProjectStatus,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** A longer public-facing summary; [notes] stays the private working scratchpad. */
    val description: String? = null,
    /** Free text ("top-down raglan", "corner-to-corner", "in the round") -- craft-neutral by design. */
    val constructionMethod: String? = null,
    /** User's own type name, shown instead of [projectType]'s label when that is [ProjectType.OTHER]. */
    val customTypeLabel: String? = null,
    /** ISO-8601 local dates ("yyyy-MM-dd"): date-only concepts per ARCHITECTURE.md §9. */
    val startDate: String? = null,
    val targetDate: String? = null,
    val completedDate: String? = null
)

enum class Craft(val storageValue: String) {
    KNITTING("KNITTING"),
    CROCHET("CROCHET"),
    TUNISIAN_CROCHET("TUNISIAN_CROCHET"),
    LOOM_KNITTING("LOOM_KNITTING"),
    OTHER("OTHER");

    companion object {
        fun fromStorageValue(value: String): Craft? =
            entries.firstOrNull { it.storageValue == value }
    }
}

enum class ProjectStatus(val storageValue: String) {
    PLANNED("PLANNED"),
    ACTIVE("ACTIVE"),
    PAUSED("PAUSED"),
    COMPLETED("COMPLETED"),
    ABANDONED("ABANDONED");

    companion object {
        fun fromStorageValue(value: String): ProjectStatus? =
            entries.firstOrNull { it.storageValue == value }
    }
}

enum class ProjectType(val storageValue: String) {
    SWEATER("SWEATER"),
    CARDIGAN("CARDIGAN"),
    TOP("TOP"),
    SOCKS("SOCKS"),
    HAT("HAT"),
    SCARF("SCARF"),
    SHAWL("SHAWL"),
    BLANKET("BLANKET"),
    BAG("BAG"),
    AMIGURUMI("AMIGURUMI"),
    HOMEWARE("HOMEWARE"),
    ACCESSORY("ACCESSORY"),
    OTHER("OTHER");

    companion object {
        fun fromStorageValue(value: String): ProjectType? =
            entries.firstOrNull { it.storageValue == value }
    }
}
