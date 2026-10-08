package com.macareen.stitchbook2.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One place in the floating bottom bar. */
data class BottomBarItem(
    val key: String,
    val label: String,
    @param:DrawableRes val icon: Int
)

/**
 * A floating white pill with drawn icons. The selected place grows into a rose
 * pill that also shows its name, so the bar stays calm but never icon-only
 * for the place you are in. Every item carries its name as a content
 * description for screen readers.
 */
@Composable
fun StitchbookBottomBar(
    items: List<BottomBarItem>,
    selectedKey: String?,
    onSelect: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                BottomBarEntry(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item) }
                )
            }
        }
    }
}

@Composable
private fun BottomBarEntry(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics { contentDescription = item.label }
            .animateContentSize()
            .height(44.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
                maxLines = 1
            )
        }
    }
}
