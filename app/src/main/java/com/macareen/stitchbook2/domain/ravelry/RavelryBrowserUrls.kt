package com.macareen.stitchbook2.domain.ravelry

import java.net.URI

/** Normal account-browser links only; never accepts arbitrary hosts or credentials. */
object RavelryBrowserUrls {
    const val LIBRARY = "https://www.ravelry.com/library"
    fun validated(value: String?): String? = try {
        val text = value?.trim().orEmpty()
        val uri = URI(if (text.startsWith("/") && !text.startsWith("//")) "https://www.ravelry.com$text" else text)
        if (uri.scheme.equals("https", true) && uri.host?.lowercase() in setOf("ravelry.com", "www.ravelry.com") &&
            uri.rawUserInfo == null && uri.port in setOf(-1, 443)) uri.toASCIIString() else null
    } catch (_: Exception) { null }
}
