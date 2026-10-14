package com.macareen.stitchbook2.domain.model

/** A named project component with its own guide executions, not a guide section or repeat. */
data class ProjectPiece(val id: String, val projectId: String, val guideId: String,
    val name: String, val isArchived: Boolean, val createdAt: Long, val updatedAt: Long)

fun normalizedPieceName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it.length <= 100 }
