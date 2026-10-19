package com.macareen.stitchbook2.domain.model

/** A document link change; original files and guide definitions are never rewritten. */
sealed interface PatternPdfChangeResult {
    data object Applied : PatternPdfChangeResult
    data object Missing : PatternPdfChangeResult
    data class Changed(val current: LibraryItem) : PatternPdfChangeResult
}
