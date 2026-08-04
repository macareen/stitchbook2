package com.macareen.stitchbook2.feature.stash

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.components.PlaceholderScreen
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun StashScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.destination_stash),
        description = stringResource(R.string.stash_description),
        icon = Icons.Outlined.Inventory2,
        iconContentDescription = stringResource(R.string.stash_icon_description),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun StashScreenPreview() {
    StitchbookTheme {
        StashScreen()
    }
}
