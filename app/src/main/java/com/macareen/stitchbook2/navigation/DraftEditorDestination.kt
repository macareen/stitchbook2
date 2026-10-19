package com.macareen.stitchbook2.navigation

import android.net.Uri

/** [PROJECT_ID_ARGUMENT] is the project the knitting belongs to; absent when the guide is opened on its own. */
object DraftEditorDestination {
    const val GUIDE_ID_ARGUMENT = "guideId"
    const val PROJECT_ID_ARGUMENT = "projectId"
    const val FROM_FOCUS_ARGUMENT = "fromFocus"
    const val ROUTE = "guides/{$GUIDE_ID_ARGUMENT}/draft?$PROJECT_ID_ARGUMENT={$PROJECT_ID_ARGUMENT}&$FROM_FOCUS_ARGUMENT={$FROM_FOCUS_ARGUMENT}"

    fun route(guideId: String, projectId: String? = null, fromFocus: Boolean = false): String {
        val base = "guides/${Uri.encode(guideId)}/draft"
        val query = listOfNotNull(projectId?.let { "$PROJECT_ID_ARGUMENT=${Uri.encode(it)}" },
            if (fromFocus) "$FROM_FOCUS_ARGUMENT=true" else null).joinToString("&")
        return if (query.isEmpty()) base else "$base?$query"
    }
}
