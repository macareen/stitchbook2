package com.macareen.stitchbook2.navigation

import android.net.Uri

object GuideFocusDestination {
    const val GUIDE_ID_ARGUMENT = "guideId"
    const val PROJECT_ID_ARGUMENT = "projectId"
    const val PIECE_ID_ARGUMENT = "pieceId"
    const val ROUTE = "guides/{$GUIDE_ID_ARGUMENT}/focus?$PROJECT_ID_ARGUMENT={$PROJECT_ID_ARGUMENT}&$PIECE_ID_ARGUMENT={$PIECE_ID_ARGUMENT}"

    fun route(guideId: String, projectId: String? = null, pieceId: String? = null): String {
        require(pieceId == null || projectId != null) { "A piece belongs to a project." }
        val base = "guides/${Uri.encode(guideId)}/focus"
        val query = listOfNotNull(projectId?.let { "$PROJECT_ID_ARGUMENT=${Uri.encode(it)}" },
            pieceId?.let { "$PIECE_ID_ARGUMENT=${Uri.encode(it)}" }).joinToString("&")
        return if (query.isEmpty()) base else "$base?$query"
    }
}
