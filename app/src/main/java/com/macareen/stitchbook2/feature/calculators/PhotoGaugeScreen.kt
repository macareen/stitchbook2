package com.macareen.stitchbook2.feature.calculators

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.net.toUri
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.data.photos.PhotoThumbnailLoader
import com.macareen.stitchbook2.domain.model.*
import java.util.Locale

/** A temporary offline measuring tool. Original photos remain in the user's chosen storage. */
@Composable
fun PhotoGaugeScreen(onBack: () -> Unit, initialPhotoUri: String? = null) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf(initialPhotoUri) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf(false) }
    var verticalGrid by rememberSaveable { mutableStateOf(false) }
    var showBounds by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var left by rememberSaveable { mutableFloatStateOf(.15f) }
    var right by rememberSaveable { mutableFloatStateOf(.85f) }
    var top by rememberSaveable { mutableFloatStateOf(.15f) }
    var bottom by rememberSaveable { mutableFloatStateOf(.85f) }
    var width by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var stitches by rememberSaveable { mutableStateOf("20") }
    var rows by rememberSaveable { mutableStateOf("28") }
    var horizontalOffset by rememberSaveable { mutableFloatStateOf(0f) }
    var verticalOffset by rememberSaveable { mutableFloatStateOf(0f) }
    var unit by rememberSaveable { mutableStateOf(SwatchLengthUnit.CENTIMETRES) }
    fun select(value: Uri) {
        uri = value.toString()
        // A different photo needs a new physical calibration.
        width = ""; height = ""; left = .15f; right = .85f; top = .15f; bottom = .85f
        horizontalOffset = 0f; verticalOffset = 0f; error = false
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = pendingCapture
        pendingCapture = null
        if (success && captured != null) select(captured.toUri()) else error = true
    }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { value ->
        if (value != null) {
            pendingCapture = value.toString()
            try { camera.launch(value) } catch (_: Exception) { pendingCapture = null; error = true }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { value ->
        if (value != null) {
            try { context.contentResolver.takePersistableUriPermission(value, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Some providers grant session access only. */ }
            select(value)
        }
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = null
        val selected = uri
        if (selected != null) {
            value = PhotoThumbnailLoader.load(context, selected, 1600)
            if (value == null) error = true
        }
    }
    val across = PhotoGauge.measure(stitches.gaugeNumber() ?: 0.0, width.gaugeNumber() ?: 0.0, unit)
    val vertical = PhotoGauge.measure(rows.gaugeNumber() ?: 0.0, height.gaugeNumber() ?: 0.0, unit)
    val result = listOfNotNull(across?.let { stringResource(R.string.photo_gauge_across_result, it.perTenCentimetres, it.perFourInches) },
        vertical?.let { stringResource(R.string.photo_gauge_vertical_result, it.perTenCentimetres, it.perFourInches) }).joinToString("\n")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.photo_gauge_back)) }
            TextButton(onClick = { help = true }) { Text(stringResource(R.string.photo_gauge_help)) }
        }
        Text(stringResource(R.string.photo_gauge_title), style = MaterialTheme.typography.headlineMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { try { picker.launch(arrayOf("image/*")) } catch (_: Exception) { error = true } }) { Text(stringResource(R.string.photo_gauge_choose)) }
            OutlinedButton(onClick = { try { destination.launch("Stitchbook-swatch.jpg") } catch (_: Exception) { error = true } }) { Text(stringResource(R.string.photo_gauge_camera)) }
        }
        if (error) Text(stringResource(R.string.photo_gauge_error), color = MaterialTheme.colorScheme.error)
        bitmap?.let { photo ->
            val window = GaugeWindow(left, top, right, bottom)
            val color = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.tertiary
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !verticalGrid, onClick = { verticalGrid = false }, label = { Text(stringResource(R.string.photo_gauge_stitches_tab)) })
                FilterChip(selected = verticalGrid, onClick = { verticalGrid = true }, label = { Text(stringResource(R.string.photo_gauge_rows_tab)) })
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val aspect = photo.width.toFloat() / photo.height
                Box(Modifier.width(minOf(maxWidth, 320.dp * aspect)).aspectRatio(aspect)) {
                Image(photo.asImageBitmap(), stringResource(R.string.photo_gauge_photo), Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val x = window.left * size.width; val y = window.top * size.height
                    val w = (window.right - window.left) * size.width; val h = (window.bottom - window.top) * size.height
                    drawRect(color.copy(alpha = .08f), Offset(x, y), Size(w, h))
                    drawRect(color, Offset(x, y), Size(w, h), style = Stroke(3.dp.toPx()))
                    if (!verticalGrid) PhotoGauge.gridLines(stitches.gaugeNumber() ?: 0.0, horizontalOffset.toDouble()).forEach {
                        drawLine(color.copy(alpha = .8f), Offset(x + it * w, y), Offset(x + it * w, y + h), 1.dp.toPx())
                    }
                    if (verticalGrid) PhotoGauge.gridLines(rows.gaugeNumber() ?: 0.0, verticalOffset.toDouble()).forEach {
                        drawLine(secondary.copy(alpha = .8f), Offset(x, y + it * h), Offset(x + w, y + it * h), 1.dp.toPx())
                    }
                }
                }
            }
            if (!verticalGrid) {
                GaugeCount(stringResource(R.string.photo_gauge_stitches), stitches, { stitches = it })
                GaugeSlider(stringResource(R.string.photo_gauge_columns_offset), horizontalOffset, 0f..1f) { horizontalOffset = it }
            } else {
                GaugeCount(stringResource(R.string.photo_gauge_rows), rows, { rows = it })
                GaugeSlider(stringResource(R.string.photo_gauge_rows_offset), verticalOffset, 0f..1f) { verticalOffset = it }
            }
            TextButton(onClick = { showBounds = !showBounds }) { Text(stringResource(R.string.photo_gauge_bounds)) }
            if (showBounds) {
                GaugeSlider(stringResource(R.string.photo_gauge_left), left, 0f..(right - .05f)) { left = it; width = "" }
                GaugeSlider(stringResource(R.string.photo_gauge_right), right, (left + .05f)..1f) { right = it; width = "" }
                GaugeSlider(stringResource(R.string.photo_gauge_top), top, 0f..(bottom - .05f)) { top = it; height = "" }
                GaugeSlider(stringResource(R.string.photo_gauge_bottom), bottom, (top + .05f)..1f) { bottom = it; height = "" }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SwatchLengthUnit.entries.forEach { choice ->
                    FilterChip(selected = unit == choice, onClick = {
                        if (choice != unit) { width = ""; height = ""; unit = choice }
                    }, label = { Text(stringResource(if (choice == SwatchLengthUnit.CENTIMETRES) R.string.swatch_centimetres else R.string.swatch_inches)) })
                }
            }
            Text(stringResource(R.string.photo_gauge_calibrate))
            GaugeField(stringResource(R.string.photo_gauge_width), width) { width = it }
            GaugeField(stringResource(R.string.photo_gauge_height), height) { height = it }
            if (result.isNotEmpty()) {
                Text(result, style = MaterialTheme.typography.titleMedium)
                val shareTitle = stringResource(R.string.photo_gauge_share)
                OutlinedButton(onClick = {
                    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, result) }, shareTitle)) }
                    catch (_: Exception) { error = true }
                }) { Text(shareTitle) }
            }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.photo_gauge_help)) },
        text = { Text(stringResource(R.string.photo_gauge_help_text)) }, confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.reference_close)) } })
}

@Composable
private fun GaugeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, changed: (Float) -> Unit) {
    Column {
        Text("$label · ${String.format(Locale.getDefault(), "%.0f", value * 100)}%")
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = changed, valueRange = range,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

@Composable
private fun GaugeField(label: String, value: String, changed: (String) -> Unit) {
    OutlinedTextField(value, changed, label = { Text(label) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun GaugeCount(label: String, value: String, changed: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(modifier = Modifier.semantics { contentDescription = "$label −" }, onClick = { changed(((value.gaugeNumber() ?: 0.0) - .5).coerceAtLeast(.5).toString()) }, enabled = (value.gaugeNumber() ?: 0.0) > .5) { Text("−") }
        Box(Modifier.weight(1f)) { GaugeField(label, value, changed) }
        OutlinedButton(modifier = Modifier.semantics { contentDescription = "$label +" }, onClick = { changed(((value.gaugeNumber() ?: 0.0) + .5).coerceAtMost(200.0).toString()) }, enabled = (value.gaugeNumber() ?: 0.0) < 200) { Text("+") }
    }
}

private fun String.gaugeNumber(): Double? = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
