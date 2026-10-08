package com.macareen.stitchbook2.domain.model

/**
 * Starts a project from a Library pattern, filled in from what the pattern
 * already says: its title, craft, a project type read from the title or
 * tags, and its description. The new project is Planned, so nothing counts
 * as started until the user starts knitting.
 */
object ProjectFromPattern {

    private val typeWords: List<Pair<Regex, ProjectType>> = listOf(
        Regex("""\b(cardigans?|cardi)\b""") to ProjectType.CARDIGAN,
        Regex("""\b(sweaters?|pullovers?|jumpers?|raglan|yoke)\b""") to ProjectType.SWEATER,
        Regex("""\b(tops?|tees?|t-shirts?|tanks?|vests?|camisoles?|blouses?|tunics?)\b""") to ProjectType.TOP,
        Regex("""\b(socks?)\b""") to ProjectType.SOCKS,
        Regex("""\b(hats?|beanies?|toques?|berets?|bonnets?|balaclavas?)\b""") to ProjectType.HAT,
        Regex("""\b(scarf|scarves|cowls?|snoods?)\b""") to ProjectType.SCARF,
        Regex("""\b(shawls?|wraps?|stoles?|shawlettes?)\b""") to ProjectType.SHAWL,
        Regex("""\b(blankets?|afghans?|throws?|quilts?)\b""") to ProjectType.BLANKET,
        Regex("""\b(bags?|totes?|pouch|pouches|baskets?)\b""") to ProjectType.BAG,
        Regex("""\b(amigurumi|plush(ie)?s?|toys?|dolls?)\b""") to ProjectType.AMIGURUMI,
        Regex("""\b(cushions?|pillows?|coasters?|dishcloths?|washcloths?|placemats?|rugs?)\b""") to ProjectType.HOMEWARE,
        Regex("""\b(mittens?|gloves?|mitts?|headbands?|ear ?warmers?|leg ?warmers?)\b""") to ProjectType.ACCESSORY
    )

    /** The first project type named in the title, then the tags; Other when none is. */
    fun typeOf(title: String, tags: List<String> = emptyList()): ProjectType {
        for (text in listOf(title) + tags) {
            val lower = text.lowercase()
            typeWords.firstOrNull { (words, _) -> words.containsMatchIn(lower) }?.let { return it.second }
        }
        return ProjectType.OTHER
    }

    fun project(pattern: LibraryItem, id: String, now: Long): Project = Project(
        id = id,
        name = pattern.title,
        craft = pattern.craft,
        projectType = typeOf(pattern.title, pattern.tags),
        status = ProjectStatus.PLANNED,
        notes = null,
        createdAt = now,
        updatedAt = now,
        description = pattern.notes
    )
}
