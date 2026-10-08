package com.macareen.stitchbook2.feature.cards

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.data.photos.PhotoThumbnailLoader
import com.macareen.stitchbook2.domain.cards.CardContent
import com.macareen.stitchbook2.domain.cards.CardField
import com.macareen.stitchbook2.domain.cards.CardTemplate
import com.macareen.stitchbook2.ui.cards.CardText
import com.macareen.stitchbook2.ui.cards.ShareCardRenderer
import com.macareen.stitchbook2.ui.components.ChoiceChipRow
import com.macareen.stitchbook2.ui.components.PhotoThumbnail
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.components.SectionHeader
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareCardRoute(viewModel: ShareCardViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShareCardScreen(
        uiState = uiState,
        onTemplateSelected = viewModel::selectTemplate,
        onFieldToggled = viewModel::toggleField,
        onPhotoSelected = viewModel::selectPhoto
    )
}

@Composable
fun ShareCardScreen(
    uiState: ShareCardUiState,
    onTemplateSelected: (CardTemplate) -> Unit,
    onFieldToggled: (CardField) -> Unit,
    onPhotoSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cardText = remember(context) { CardText(context.resources) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }

    val content = uiState.content
    // The preview *is* the exported bitmap, so what you see is exactly what is saved or shared.
    LaunchedEffect(content) {
        preview = content?.let { render(context, it, cardText) }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = preview
        if (uri != null && bitmap != null) {
            coroutineScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }.getOrNull() == true
                }
                message = if (saved) R.string.card_saved else R.string.card_export_failed
            }
        }
    }

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (uiState.loadFailed || content == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.card_load_failed))
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(StitchbookSpacing.large),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
    ) {
        Text(text = stringResource(R.string.card_title), style = MaterialTheme.typography.headlineMedium)
        QuietText(text = stringResource(R.string.card_privacy_note))

        ChoiceChipRow(
            options = uiState.templates,
            selected = uiState.template,
            optionLabel = { cardText.templateLabel(it) },
            onSelected = onTemplateSelected
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ShareCardRenderer.WIDTH.toFloat() / ShareCardRenderer.HEIGHT)
        ) {
            val bitmap = preview
            if (bitmap == null) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = cardText.description(content),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            SecondaryActionButton(
                text = stringResource(R.string.card_save),
                icon = Icons.Outlined.Download,
                enabled = preview != null,
                onClick = { saveLauncher.launch(cardFileName(uiState.template)) },
                modifier = Modifier.weight(1f)
            )
            PrimaryActionButton(
                text = stringResource(R.string.card_share),
                icon = Icons.Outlined.Share,
                enabled = preview != null,
                onClick = {
                    preview?.let { bitmap ->
                        coroutineScope.launch {
                            val shared = share(context, bitmap, uiState.template)
                            if (!shared) message = R.string.card_export_failed
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        message?.let { QuietText(text = stringResource(it)) }

        SectionHeader(title = stringResource(R.string.card_fields))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            uiState.availableFields.forEach { field ->
                FilterChip(
                    selected = field in uiState.fields,
                    onClick = { onFieldToggled(field) },
                    label = { Text(text = stringResource(field.labelResource())) }
                )
            }
        }
        if (CardField.NOTES in uiState.fields) {
            Text(
                text = stringResource(R.string.card_private_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (CardField.PHOTO in uiState.availableFields && uiState.photos.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.card_photo))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                items(uiState.photos, key = { it.id }) { photo ->
                    val selected = photo.id == uiState.selectedPhotoId && CardField.PHOTO in uiState.fields
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        PhotoThumbnail(
                            uri = photo.uri,
                            contentDescription = photo.caption ?: photo.displayName,
                            maxSizePx = 256,
                            modifier = Modifier
                                .size(88.dp)
                                .clickable(onClickLabel = stringResource(R.string.card_use_photo)) { onPhotoSelected(photo.id) }
                        )
                    }
                }
            }
        } else if (uiState.template == CardTemplate.BEFORE_AFTER && content.photoUris.size < 2) {
            QuietText(text = stringResource(R.string.card_before_after_missing))
        }

        SectionHeader(title = stringResource(R.string.card_description_title))
        val description = cardText.description(content)
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.card_description_title), description))
                message = R.string.card_description_copied
            }
        ) { Text(text = stringResource(R.string.card_copy_description)) }
        Spacer(modifier = Modifier.height(StitchbookSpacing.large))
    }
}

private suspend fun render(context: Context, content: CardContent, text: CardText): Bitmap {
    val photos = content.photoUris.mapNotNull { PhotoThumbnailLoader.loadForExport(context, it) }
    return withContext(Dispatchers.Default) { ShareCardRenderer.render(content, photos, text) }
}

/**
 * Writes the PNG to a reproducible cache file (overwritten each time) and
 * hands it to the Android share sheet through a FileProvider. Nothing is
 * posted automatically; the user picks the destination.
 */
private suspend fun share(context: Context, bitmap: Bitmap, template: CardTemplate): Boolean {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "cards").apply { mkdirs() }
            val file = File(directory, cardFileName(template))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    } ?: return false
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.card_share)))
    return true
}

private fun cardFileName(template: CardTemplate): String =
    "stitchbook_${template.name.lowercase()}_${LocalDate.now()}.png"

private fun CardField.labelResource(): Int = when (this) {
    CardField.PHOTO -> R.string.card_field_photo
    CardField.CRAFT_AND_TYPE -> R.string.card_field_craft
    CardField.DATES -> R.string.card_field_dates
    CardField.TIME -> R.string.card_field_time
    CardField.ROWS -> R.string.card_field_rows
    CardField.MILESTONE -> R.string.card_field_milestone
    CardField.YARN -> R.string.card_field_yarn
    CardField.DESCRIPTION -> R.string.card_field_description
    CardField.NOTES -> R.string.card_field_notes
}
