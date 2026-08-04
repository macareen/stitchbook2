package com.macareen.stitchbook2.domain.model

fun normalizedProjectName(value: String): String? {
    return value.trim().takeIf { it.isNotEmpty() }
}
