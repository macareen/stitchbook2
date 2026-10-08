package com.macareen.stitchbook2.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A soft tile colour with the deeper yarn and ink tones drawn on top of it.
 * Used for illustrated stand-ins (a skein in the project's colour) until the
 * user adds a real photo.
 */
data class Pastel(val tile: Color, val yarn: Color, val ink: Color)

object CozyPastels {
    val Peach = Pastel(tile = Color(0xFFF6D9CF), yarn = Color(0xFFE7A27E), ink = Color(0xFF9C6B60))
    val Sky = Pastel(tile = Color(0xFFDCE9F2), yarn = Color(0xFFA9CBE3), ink = Color(0xFF4F7493))
    val Sage = Pastel(tile = Color(0xFFE6E9D6), yarn = Color(0xFF9AA46E), ink = Color(0xFF6B7448))
    val Butter = Pastel(tile = Color(0xFFFBEBD6), yarn = Color(0xFFE9BE7E), ink = Color(0xFF8A5A23))
    val Blush = Pastel(tile = Color(0xFFFBE1E2), yarn = Color(0xFFEE8F94), ink = Color(0xFF8E3445))
    val Lilac = Pastel(tile = Color(0xFFEAE1F0), yarn = Color(0xFFB9A2CF), ink = Color(0xFF65507A))

    private val all = listOf(Peach, Sky, Sage, Butter, Blush, Lilac)

    /** A stable pastel for an item, so the same project always gets the same colour. */
    fun forKey(key: String): Pastel = all[Math.floorMod(key.hashCode(), all.size)]
}
