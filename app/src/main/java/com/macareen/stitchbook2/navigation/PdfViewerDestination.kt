package com.macareen.stitchbook2.navigation

import android.net.Uri

object PdfViewerDestination {
    const val LIBRARY_ITEM_ID_ARGUMENT = "libraryItemId"
    const val ROUTE = "library/{$LIBRARY_ITEM_ID_ARGUMENT}/pdf"

    fun route(libraryItemId: String): String {
        return "library/${Uri.encode(libraryItemId)}/pdf"
    }
}
