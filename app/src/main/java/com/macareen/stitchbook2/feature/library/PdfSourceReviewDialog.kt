package com.macareen.stitchbook2.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.parsing.PdfSourceRegion
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Temporary selection only; the reviewed text becomes a normal draft, with source notes. */
@Composable
internal fun PdfSourceReviewDialog(
    review: PdfSourceReview,
    onRegions: (List<PdfSourceRegion>) -> Unit,
    onPreview: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    var page by rememberSaveable { mutableIntStateOf(1) }
    var editing by rememberSaveable { mutableIntStateOf(0) }
    var reviewed by rememberSaveable(review.preview) { mutableStateOf(false) }
    val selected = review.regions.getOrNull(editing)
    Dialog(onDismissRequest = { if (!review.busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !review.busy)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.pdf_source_title, review.size), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f).testTag("pdf-source-review"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(stringResource(R.string.pdf_source_help))
                        FlowRow {
                            TextButton(onClick = { page-- }, enabled = page > 1 && !review.busy) { Text(stringResource(R.string.pdf_viewer_previous_page)) }
                            Text(stringResource(R.string.pdf_source_page, page, review.original.pageCount), Modifier.padding(12.dp))
                            TextButton(onClick = { page++ }, enabled = page < review.original.pageCount && !review.busy) { Text(stringResource(R.string.pdf_viewer_next_page)) }
                        }
                        SourcePagePreview(review.bytes, page, review.regions.filter { it.page == page })
                        TextButton(onClick = {
                            onRegions(review.regions + PdfSourceRegion(page)); editing = review.regions.size
                        }, enabled = !review.busy && review.regions.none { it.page == page } && review.regions.size < 500) {
                            Text(stringResource(R.string.pdf_source_add_page))
                        }
                    }
                    item { Text(stringResource(R.string.pdf_source_order), style = MaterialTheme.typography.titleMedium) }
                    itemsIndexed(review.regions) { index, region ->
                        Surface(color = if (index == editing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(8.dp)) {
                                Text(stringResource(R.string.pdf_source_region, index + 1, region.page,
                                    region.left, region.top, region.right, region.bottom))
                                FlowRow {
                                    TextButton(onClick = { editing = index; page = region.page }, enabled = !review.busy) { Text(stringResource(R.string.pdf_source_adjust)) }
                                    TextButton(onClick = {
                                        val regions = review.regions.toMutableList()
                                        regions[index] = regions[index - 1].also { regions[index - 1] = region }
                                        onRegions(regions); editing = index - 1; page = region.page
                                    }, enabled = !review.busy && index > 0) { Text(stringResource(R.string.pdf_source_up)) }
                                    TextButton(onClick = {
                                        val regions = review.regions.toMutableList()
                                        regions[index] = regions[index + 1].also { regions[index + 1] = region }
                                        onRegions(regions); editing = index + 1; page = region.page
                                    }, enabled = !review.busy && index < review.regions.lastIndex) { Text(stringResource(R.string.pdf_source_down)) }
                                    TextButton(onClick = {
                                        onRegions(review.regions.filterIndexed { i, _ -> i != index }); editing = 0
                                    }, enabled = !review.busy) { Text(stringResource(R.string.pdf_source_exclude)) }
                                }
                            }
                        }
                    }
                    if (selected != null) item {
                        Text(stringResource(R.string.pdf_source_edit_region, editing + 1), style = MaterialTheme.typography.titleMedium)
                        fun update(region: PdfSourceRegion) {
                            onRegions(review.regions.mapIndexed { i, old -> if (i == editing) region else old })
                            page = region.page
                        }
                        RegionEdge(stringResource(R.string.pdf_source_left), selected.left, 0, selected.right - 1, !review.busy) { update(selected.copy(left = it)) }
                        RegionEdge(stringResource(R.string.pdf_source_top), selected.top, 0, selected.bottom - 1, !review.busy) { update(selected.copy(top = it)) }
                        RegionEdge(stringResource(R.string.pdf_source_right), selected.right, selected.left + 1, 100, !review.busy) { update(selected.copy(right = it)) }
                        RegionEdge(stringResource(R.string.pdf_source_bottom), selected.bottom, selected.top + 1, 100, !review.busy) { update(selected.copy(bottom = it)) }
                        TextButton(onClick = {
                            val middle = (selected.left + selected.right) / 2
                            onRegions(review.regions.take(editing) + listOf(selected.copy(right = middle), selected.copy(left = middle)) + review.regions.drop(editing + 1))
                        }, enabled = !review.busy && selected.right - selected.left >= 2 && review.regions.size < 500) {
                            Text(stringResource(R.string.pdf_source_split_columns))
                        }
                    }
                    item {
                        TextButton(onClick = onPreview, enabled = !review.busy && !review.changed && review.regions.isNotEmpty(),
                            modifier = Modifier.testTag("pdf-source-preview")) { Text(stringResource(R.string.pdf_source_preview)) }
                        if (review.busy) CircularProgressIndicator()
                        if (review.changed) Text(stringResource(R.string.pdf_source_changed), color = MaterialTheme.colorScheme.error)
                        review.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    review.preview?.let { preview ->
                        item { Text(stringResource(R.string.pdf_source_text), style = MaterialTheme.typography.titleMedium) }
                        itemsIndexed(preview.selected.lines) { _, line ->
                            Text("p.${line.source.pageNumber} · ${line.text}")
                        }
                        item {
                            Row {
                                Checkbox(reviewed, { reviewed = it }, enabled = !review.busy && !review.changed)
                                Text(stringResource(R.string.pdf_source_confirm))
                            }
                        }
                    }
                }
                FlowRow {
                    TextButton(onClick = onCreate, enabled = reviewed && review.preview != null && review.problem == null && !review.changed && !review.busy,
                        modifier = Modifier.testTag("pdf-source-create")) { Text(stringResource(R.string.pattern_guides_create)) }
                    TextButton(onClick = onDismiss, enabled = !review.busy) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun RegionEdge(label: String, value: Int, minimum: Int, maximum: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Text("$label: $value%")
    Slider(value.toFloat(), { onChange(it.roundToInt().coerceIn(minimum, maximum)) },
        modifier = Modifier.semantics { contentDescription = "$label: $value%" },
        valueRange = minimum.toFloat()..maximum.coerceAtLeast(minimum + 1).toFloat(), enabled = enabled && maximum > minimum)
}

@Composable
private fun SourcePagePreview(bytes: ByteArray, page: Int, regions: List<PdfSourceRegion>) {
    var bitmap by remember(bytes, page) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(bytes, page) { mutableStateOf(false) }
    LaunchedEffect(bytes, page) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                PDDocument.load(bytes).use {
                    val box = it.getPage(page - 1).cropBox
                    PDFRenderer(it).renderImage(page - 1, minOf(1f, 1400f / maxOf(box.width, box.height)))
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error
        } catch (_: Exception) { failed = true }
    }
    val image = bitmap
    DisposableEffect(image) { onDispose { image?.recycle() } }
    if (image != null) {
        Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height)) {
            Image(image.asImageBitmap(), stringResource(R.string.pdf_source_original), Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                regions.forEach { region ->
                    val offset = Offset(size.width * region.left / 100f, size.height * region.top / 100f)
                    val area = Size(size.width * (region.right - region.left) / 100f, size.height * (region.bottom - region.top) / 100f)
                    drawRect(Color(0xFF00695C).copy(alpha = .12f), offset, area)
                    drawRect(Color(0xFF00695C), offset, area, style = Stroke(3.dp.toPx()))
                }
            }
        }
    } else if (failed) Text(stringResource(R.string.pdf_source_image_failed)) else CircularProgressIndicator()
}
