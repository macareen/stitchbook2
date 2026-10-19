package com.macareen.stitchbook2.domain.model

/** A named project component with its own guide executions, not a guide section or repeat. */
data class ProjectPiece(val id: String, val projectId: String, val guideId: String,
    val name: String, val isArchived: Boolean, val createdAt: Long, val updatedAt: Long)

fun normalizedPieceName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it.length <= 100 }

const val MAX_PIECES_PER_BATCH = 100

/** Materialize a quantity as separate named pieces, each with independent progress. */
fun pieceNamesForQuantity(baseName: String, quantity: Int): List<String>? {
    val name = normalizedPieceName(baseName) ?: return null
    if (quantity !in 1..MAX_PIECES_PER_BATCH) return null
    val names = if (quantity == 1) listOf(name) else (1..quantity).map { "$name $it" }
    return names.takeIf { result -> result.all { normalizedPieceName(it) == it } }
}
