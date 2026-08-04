package com.macareen.stitchbook2.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.components.PlaceholderScreen
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.destination_library),
        description = stringResource(R.string.library_description),
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        iconContentDescription = stringResource(R.string.library_icon_description),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    StitchbookTheme {
        LibraryScreen()
    }
}
