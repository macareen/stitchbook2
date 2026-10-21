package com.macareen.stitchbook2.domain.parsing

/** A rectangle in displayed-page coordinates, measured from the top left in percent. */
data class PdfSourceRegion(
    val page: Int,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 100,
    val bottom: Int = 100
) {
    init {
        require(page >= 1)
        require(left in 0..99 && right in 1..100 && left < right)
        require(top in 0..99 && bottom in 1..100 && top < bottom)
    }

    val isWholePage: Boolean get() = left == 0 && top == 0 && right == 100 && bottom == 100
}

/** Regions are read in this exact order. Overlap is refused to avoid duplicated instructions. */
data class PdfSourceSelection(val regions: List<PdfSourceRegion>) {
    init {
        require(regions.isNotEmpty())
        require(regions.size <= 500)
        regions.forEachIndexed { index, region ->
            require(regions.take(index).none { other ->
                region.page == other.page && region.left < other.right && other.left < region.right &&
                    region.top < other.bottom && other.top < region.bottom
            }) { "Selected regions must not overlap." }
        }
    }

    fun validatePageCount(pageCount: Int) {
        require(regions.all { it.page <= pageCount }) { "Selected page is outside the PDF." }
    }

    fun description(): String = regions.mapIndexed { index, r ->
        "${index + 1}. p.${r.page}: left ${r.left}%, top ${r.top}%, right ${r.right}%, bottom ${r.bottom}%"
    }.joinToString("\n")
}

/** Immutable preview: creation consumes exactly the text that was reviewed, without re-extracting. */
data class ReviewedPdfSource(
    val original: ExtractedDocument,
    val selected: ExtractedDocument,
    val selection: PdfSourceSelection
)
