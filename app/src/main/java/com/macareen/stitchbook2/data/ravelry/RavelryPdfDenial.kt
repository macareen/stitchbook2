package com.macareen.stitchbook2.data.ravelry

import java.util.Locale
import org.json.JSONObject

/** Only these fixed categories may leave the response-handling boundary. */
enum class RavelryPdfDenial {
    UNKNOWN, HTML_RESPONSE, OAUTH_REQUIRED, PDF_PERMISSION_REQUIRED, FILE_ACCESS, ATTACHMENT_REFERENCE;

    companion object {
        fun classify(body: String): RavelryPdfDenial {
            if (body.length > 65_536) return UNKNOWN
            val trimmed = body.trimStart()
            if (trimmed.startsWith("<")) return HTML_RESPONSE
            val text = try {
                val root = JSONObject(body)
                if (root.optString("required_scope").trim().equals("library-pdf", true)) return PDF_PERMISSION_REQUIRED
                listOf("error", "message", "error_description", "scope", "required_scope").mapNotNull { key ->
                    when (val value = root.opt(key)) {
                        is String -> value
                        is JSONObject -> listOf("message", "description").mapNotNull { value.opt(it) as? String }.joinToString(" ")
                        else -> null
                    }
                }.joinToString(" ").lowercase(Locale.ROOT)
            } catch (_: Exception) { return UNKNOWN }
            return when {
                "oauth" in text && "not required" !in text && "does not require" !in text &&
                    listOf("required", "requires", "must use").any { it in text } -> OAUTH_REQUIRED
                "library-pdf" in text && listOf("scope", "permission", "required", "missing").any { it in text } -> PDF_PERMISSION_REQUIRED
                listOf("not owned", "does not own", "not in your library", "not purchased").any { it in text } -> FILE_ACCESS
                listOf("invalid attachment", "attachment not found").any { it in text } -> ATTACHMENT_REFERENCE
                else -> UNKNOWN
            }
        }
    }
}
