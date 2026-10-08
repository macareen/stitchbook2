package com.macareen.stitchbook2.navigation

import android.net.Uri

object PdfViewerDestination {
    const val LIBRARY_ITEM_ID_ARGUMENT = "libraryItemId"
    const val ROUTE = "library/{$LIBRARY_ITEM_ID_ARGUMENT}/pdf"

    fun route(libraryItemId: String): String {
        return "library/${Uri.encode(libraryItemId)}/pdf"
    }
}

/** A pattern's own screen: its file and its guides by size. */
object PatternGuidesDestination {
    const val ROUTE = "library/{${PdfViewerDestination.LIBRARY_ITEM_ID_ARGUMENT}}/guides"

    fun route(libraryItemId: String): String = "library/${Uri.encode(libraryItemId)}/guides"
}
