package com.macareen.stitchbook2.domain.ravelry

import java.net.URI

/** Public image references only; credentials and plain HTTP are never accepted. */
object RavelryPhotoUrls {
    fun safe(value: String?): String? = value?.trim()?.takeIf { text ->
        runCatching {
            val uri = URI(text)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                (uri.port == -1 || uri.port == 443)
        }.getOrDefault(false)
    }
}
