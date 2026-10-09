package com.macareen.stitchbook2.domain.ravelry

import java.io.IOException
import java.io.OutputStream

/**
 * A Ravelry "Basic Auth: personal account access" key pair, typed in by
 * the person on their own device. [accessKey] is Ravelry's basic-auth
 * username (not the account name) and [personalKey] its password.
 */
data class RavelryCredentials(val accessKey: String, val personalKey: String) {
    init {
        require(accessKey.isNotBlank()) { "Access key must not be blank." }
        require(personalKey.isNotBlank()) { "Personal key must not be blank." }
    }
}

/** Keeps the key on this device only, encrypted at rest. Never exported or backed up. */
interface RavelryCredentialStore {
    fun load(): RavelryCredentials?
    fun save(credentials: RavelryCredentials)
    fun clear()
}

/** One stash entry as Ravelry describes it, reduced to what Stitchbook keeps. */
data class RavelryStashEntry(
    val id: Long,
    val name: String?,
    val yarnId: Long?,
    val yarnName: String?,
    val companyName: String?,
    val weightName: String?,
    val colorway: String?,
    val dyeLot: String?,
    val location: String?,
    val skeins: Double?,
    val yardsPerSkein: Double?,
    val gramsPerSkein: Double?
)

/** One needle or hook record. Ravelry's sizes arrive as display text, kept as-is. */
data class RavelryNeedle(
    val id: Long,
    val name: String?,
    val typeName: String?,
    val metricName: String?,
    val length: String?,
    val comment: String?
)

/** A project from the person's Ravelry notebook ("Project (small)"). Dates are Ravelry's text, normalised later. */
data class RavelryProject(
    val id: Long,
    val name: String?,
    val craftName: String?,
    val statusName: String?,
    val patternName: String?,
    val started: String?,
    val completed: String?,
    val finishBy: String?
)

/** A volume in the person's Ravelry library: a purchased or downloaded pattern, book, or magazine. */
data class RavelryVolume(
    val id: Long,
    val title: String?,
    val authorName: String?,
    val patternId: Long?
)

/** One file attached to a library volume (a pattern PDF, or sometimes a zip or chart). */
data class RavelryAttachment(val id: Long, val fileName: String?)

/**
 * Read-only access to the signed-in person's own Ravelry records.
 * Stitchbook never changes anything on Ravelry: every call is a GET except
 * [downloadLink], a POST that only asks Ravelry for a short-lived link to a
 * file the person already owns.
 */
interface RavelryApi {
    /** The Ravelry account name the key belongs to, needed in every other path. */
    suspend fun currentUsername(credentials: RavelryCredentials): String
    suspend fun stash(credentials: RavelryCredentials, username: String): List<RavelryStashEntry>
    suspend fun needles(credentials: RavelryCredentials, username: String): List<RavelryNeedle>
    suspend fun projects(credentials: RavelryCredentials, username: String): List<RavelryProject>

    /** Every volume in the person's library, including Ravelry PDF purchases and downloads. */
    suspend fun library(credentials: RavelryCredentials, username: String): List<RavelryVolume>

    /** The files attached to one library volume. */
    suspend fun volumeAttachments(credentials: RavelryCredentials, volumeId: Long): List<RavelryAttachment>

    /** A short-lived address the attachment can be downloaded from. */
    suspend fun downloadLink(credentials: RavelryCredentials, attachmentId: Long): String

    /** Writes the file at [url] (from [downloadLink]) into [into]. The key is not sent with it. */
    suspend fun downloadFile(url: String, into: OutputStream)
}

/** Ravelry refused the key (HTTP 401/403): it was mistyped, revoked, or is a read-only key. */
class RavelryAuthException(message: String) : IOException(message)

/** Any other failure talking to Ravelry; the local library is never changed when this happens. */
class RavelryUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)
