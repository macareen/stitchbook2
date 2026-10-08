package com.macareen.stitchbook2.navigation

import android.net.Uri

object ProjectDestination {
    const val PROJECT_ID_ARGUMENT = "projectId"
    const val CREATE_ROUTE = "projects/create"
    const val DETAIL_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}"
    const val EDIT_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}/edit"
    const val MATERIALS_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}/materials"
    const val JOURNAL_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}/journal"
    const val SESSIONS_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}/sessions"
    const val STATISTICS_ROUTE = "statistics"
    const val CARD_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}/card"
    const val SUMMARY_CARD_ROUTE = "statistics/card"

    fun detailRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}"
    }

    fun editRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}/edit"
    }

    fun materialsRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}/materials"
    }

    fun journalRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}/journal"
    }

    fun sessionsRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}/sessions"
    }

    fun cardRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}/card"
    }
}
