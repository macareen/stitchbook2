package com.macareen.stitchbook2.data.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Keeps read access to a user-picked document across restarts. Returns
 * false if the provider doesn't offer persistable grants (the reference is
 * still saved; a later failure surfaces as "missing -- relink").
 */
fun persistReadAccess(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    true
} catch (_: SecurityException) {
    false
}

/** The file name the provider reports, captured so a missing file can still be identified. */
fun documentDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (_: Exception) {
    null
}
