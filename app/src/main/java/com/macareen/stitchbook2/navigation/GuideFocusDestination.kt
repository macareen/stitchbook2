package com.macareen.stitchbook2.navigation

import android.net.Uri

object GuideFocusDestination {
    const val GUIDE_ID_ARGUMENT = "guideId"
    const val ROUTE = "guides/{$GUIDE_ID_ARGUMENT}/focus"

    fun route(guideId: String): String {
        return "guides/${Uri.encode(guideId)}/focus"
    }
}
