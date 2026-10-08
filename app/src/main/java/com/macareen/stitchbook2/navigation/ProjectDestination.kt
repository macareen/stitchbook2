package com.macareen.stitchbook2.navigation

import android.net.Uri

object ProjectDestination {
    const val PROJECT_ID_ARGUMENT = "projectId"
    const val CREATE_ROUTE = "projects/create"
    const val DETAIL_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}"
    const val EDIT_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}/edit"
    const val MATERIALS_ROUTE = "projects/{$PROJECT_ID_ARGUMENT}/materials"

    fun detailRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}"
    }

    fun editRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}/edit"
    }

    fun materialsRoute(projectId: String): String {
        return "projects/${Uri.encode(projectId)}/materials"
    }
}
