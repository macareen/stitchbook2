package com.macareen.stitchbook2.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.components.PlaceholderScreen
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.destination_home),
        description = stringResource(R.string.home_description),
        icon = Icons.Outlined.Home,
        iconContentDescription = stringResource(R.string.home_icon_description),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    StitchbookTheme {
        HomeScreen()
    }
}
