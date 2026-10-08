package com.macareen.stitchbook2.navigation

import android.net.Uri

/** [PAGE_ARGUMENT] is a 1-based page to open at; absent or below 1 means the last page viewed. */
object PdfViewerDestination {
    const val LIBRARY_ITEM_ID_ARGUMENT = "libraryItemId"
    const val PAGE_ARGUMENT = "page"
    const val ROUTE = "library/{$LIBRARY_ITEM_ID_ARGUMENT}/pdf?$PAGE_ARGUMENT={$PAGE_ARGUMENT}"

    fun route(libraryItemId: String, page: Int? = null): String {
        val base = "library/${Uri.encode(libraryItemId)}/pdf"
        return if (page == null) base else "$base?$PAGE_ARGUMENT=$page"
    }
}

/** A pattern's own screen: its file and its guides by size. */
object PatternGuidesDestination {
    const val ROUTE = "library/{${PdfViewerDestination.LIBRARY_ITEM_ID_ARGUMENT}}/guides"

    fun route(libraryItemId: String): String = "library/${Uri.encode(libraryItemId)}/guides"
}
