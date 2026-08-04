package com.macareen.stitchbook2.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.components.PlaceholderScreen
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.destination_settings),
        description = stringResource(R.string.settings_description),
        icon = Icons.Outlined.Settings,
        iconContentDescription = stringResource(R.string.settings_icon_description),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    StitchbookTheme {
        SettingsScreen()
    }
}
