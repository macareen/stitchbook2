package com.macareen.stitchbook2.navigation

import android.net.Uri

/** [PROJECT_ID_ARGUMENT] is the project the knitting belongs to; absent when the guide is opened on its own. */
object DraftEditorDestination {
    const val GUIDE_ID_ARGUMENT = "guideId"
    const val PROJECT_ID_ARGUMENT = "projectId"
    const val ROUTE = "guides/{$GUIDE_ID_ARGUMENT}/draft?$PROJECT_ID_ARGUMENT={$PROJECT_ID_ARGUMENT}"

    fun route(guideId: String, projectId: String? = null): String {
        val base = "guides/${Uri.encode(guideId)}/draft"
        return if (projectId == null) base else "$base?$PROJECT_ID_ARGUMENT=${Uri.encode(projectId)}"
    }
}
