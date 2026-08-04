package com.macareen.stitchbook2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.ui.theme.buttonLabel

private val MinimumTouchTarget = 48.dp

/**
 * The unmistakable primary action on a screen. Filled, full accent color —
 * reserve this for the one action a screen wants a user to take next.
 *
 * Disabled styling intentionally does not rely on opacity alone: Material3's
 * default disabled button colors substitute a neutral on-surface tone for
 * the container, rather than merely fading the accent color, so disabled
 * state remains distinguishable for users who have difficulty perceiving
 * reduced contrast/opacity.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinimumTouchTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.buttonLabel)
    }
}

/**
 * A clearly secondary action: outlined rather than filled, so hierarchy
 * reads from shape/fill rather than color alone.
 */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinimumTouchTarget),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.buttonLabel)
    }
}
