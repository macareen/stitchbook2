package com.macareen.stitchbook2.feature.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

@Composable
fun PdfViewerRoute(viewModel: PdfViewerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PdfViewerScreen(
        uiState = uiState,
        onPageChanged = viewModel::updateLastViewedPage
    )
}

@Composable
fun PdfViewerScreen(
    uiState: PdfViewerUiState,
    onPageChanged: (LibraryItem, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        PdfViewerUiState.Loading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        PdfViewerUiState.NotFound -> {
            ViewerMessage(
                title = stringResource(R.string.pdf_viewer_not_found_title),
                description = stringResource(R.string.pdf_viewer_not_found_description),
                modifier = modifier
            )
        }

        is PdfViewerUiState.Content -> {
            PdfPageViewer(
                item = uiState.item,
                onPageChanged = { page -> onPageChanged(uiState.item, page) },
                modifier = modifier
            )
        }
    }
}

private sealed interface PageLoadState {
    data object Loading : PageLoadState
    data object AccessError : PageLoadState
    data class Ready(val pageCount: Int) : PageLoadState
}

@Composable
private fun PdfPageViewer(
    item: LibraryItem,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var renderer by remember(item.pdfUri) { mutableStateOf<PdfRenderer?>(null) }
    var loadState by remember(item.pdfUri) { mutableStateOf<PageLoadState>(PageLoadState.Loading) }
    var currentPage by remember(item.pdfUri) {
        mutableIntStateOf(item.pdfLastViewedPage?.coerceAtLeast(0) ?: 0)
    }
    var bitmap by remember(item.pdfUri) { mutableStateOf<Bitmap?>(null) }

    // Owns the PdfRenderer/ParcelFileDescriptor pair's lifetime -- both must
    // be closed, and re-opened whenever the attached PDF itself changes.
    DisposableEffect(item.pdfUri) {
        var pfd: ParcelFileDescriptor? = null
        var opened: PdfRenderer? = null
        val uri = item.pdfUri?.let { Uri.parse(it) }
        if (uri == null) {
            loadState = PageLoadState.AccessError
        } else {
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    loadState = PageLoadState.AccessError
                } else {
                    opened = PdfRenderer(pfd)
                    currentPage = currentPage.coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))
                    renderer = opened
                    loadState = PageLoadState.Ready(opened.pageCount)
                }
            } catch (_: Exception) {
                // SecurityException (permission revoked), FileNotFoundException
                // (file moved/deleted), or a malformed PDF all read the same
                // to a reader: this file can't be opened right now.
                loadState = PageLoadState.AccessError
            }
        }
        onDispose {
            opened?.close()
            pfd?.close()
        }
    }

    val readyState = loadState as? PageLoadState.Ready
    LaunchedEffect(renderer, currentPage, readyState) {
        val activeRenderer = renderer ?: return@LaunchedEffect
        if (readyState == null || currentPage !in 0 until readyState.pageCount) return@LaunchedEffect
        val page = activeRenderer.openPage(currentPage)
        val rendered = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        rendered.eraseColor(AndroidColor.WHITE)
        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        bitmap = rendered
        onPageChanged(currentPage)
    }

    when (val state = loadState) {
        PageLoadState.AccessError -> {
            ViewerMessage(
                title = stringResource(R.string.pdf_viewer_access_revoked_title),
                description = stringResource(R.string.pdf_viewer_access_revoked_description),
                modifier = modifier
            )
        }

        PageLoadState.Loading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is PageLoadState.Ready -> {
            Column(modifier = modifier.fillMaxSize()) {
                val currentBitmap = bitmap
                if (currentBitmap == null) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = item.pdfFileName ?: item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(StitchbookSpacing.small)
                    )
                }

                PdfViewerControls(
                    item = item,
                    currentPage = currentPage,
                    pageCount = state.pageCount,
                    onPreviousPage = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                    onNextPage = { currentPage = (currentPage + 1).coerceAtMost(state.pageCount - 1) }
                )
            }
        }
    }
}

@Composable
private fun PdfViewerControls(
    item: LibraryItem,
    currentPage: Int,
    pageCount: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    val context = LocalContext.current
    var openFailed by remember(item.pdfUri) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(StitchbookSpacing.small)) {
        if (openFailed) {
            QuietText(text = stringResource(R.string.pdf_viewer_no_activity))
            Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousPage, enabled = currentPage > 0) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.pdf_viewer_previous_page)
                )
            }

            Text(
                text = stringResource(R.string.pdf_viewer_page_indicator, currentPage + 1, pageCount),
                style = MaterialTheme.typography.bodyMedium
            )

            IconButton(onClick = onNextPage, enabled = currentPage < pageCount - 1) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.pdf_viewer_next_page)
                )
            }

            IconButton(
                onClick = {
                    val uri = item.pdfUri?.let { Uri.parse(it) }
                    if (uri == null) {
                        openFailed = true
                        return@IconButton
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                        openFailed = false
                    } catch (_: ActivityNotFoundException) {
                        openFailed = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.pdf_viewer_open_externally_action)
                )
            }
        }
    }
}

@Composable
private fun ViewerMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StitchbookSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
