package com.macareen.stitchbook2.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.data.photos.PhotoThumbnailLoader
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.textSecondary

sealed interface PhotoLoadState {
    data object Loading : PhotoLoadState
    data class Loaded(val image: ImageBitmap) : PhotoLoadState
    data object Missing : PhotoLoadState
}

@Composable
fun rememberPhotoLoadState(uri: String, maxSizePx: Int = 512): PhotoLoadState {
    val context = LocalContext.current
    val state by produceState<PhotoLoadState>(PhotoLoadState.Loading, uri, maxSizePx) {
        value = PhotoThumbnailLoader.load(context, uri, maxSizePx)
            ?.let { PhotoLoadState.Loaded(it.asImageBitmap()) }
            ?: PhotoLoadState.Missing
    }
    return state
}

/**
 * A rounded, cropped photo tile. A missing original (moved, deleted, or
 * permission revoked) shows a clear placeholder instead of failing, so the
 * caller can offer relinking (Phase 7 acceptance criteria).
 */
@Composable
fun PhotoThumbnail(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxSizePx: Int = 512
) {
    val state = rememberPhotoLoadState(uri, maxSizePx)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            PhotoLoadState.Loading -> Unit
            is PhotoLoadState.Loaded -> Image(
                bitmap = state.image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            PhotoLoadState.Missing -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.textSecondary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = stringResource(R.string.photo_missing_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(StitchbookSpacing.extraSmall)
                )
            }
        }
    }
}
