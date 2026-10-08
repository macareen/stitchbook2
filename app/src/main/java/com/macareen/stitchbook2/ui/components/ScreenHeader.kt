package com.macareen.stitchbook2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** One secondary action shown in a [ScreenHeader]'s overflow menu. */
data class HeaderAction(val label: String, val onClick: () -> Unit)

/**
 * A screen title (or, under a host that shows the title, just its line of
 * context) with its occasional actions (bulk tools, CSV import and
 * export) tucked into one overflow menu, so the screen opens on its content
 * rather than a row of buttons.
 */
@Composable
fun ScreenHeader(
    title: String?,
    subtitle: String?,
    menuDescription: String,
    actions: List<HeaderAction>,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            title?.let { Text(text = it, style = MaterialTheme.typography.headlineMedium) }
            subtitle?.let { QuietText(text = it) }
        }
        if (actions.isNotEmpty()) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = menuDescription)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                menuOpen = false
                                action.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
