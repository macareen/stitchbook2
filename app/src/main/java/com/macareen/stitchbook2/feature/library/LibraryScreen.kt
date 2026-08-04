package com.macareen.stitchbook2.feature.library

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.feature.projects.labelResource
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onOpenPdf: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryScreen(
        uiState = uiState,
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onCraftFilterChanged = viewModel::updateCraftFilter,
        onBookmarksOnlyChanged = viewModel::updateBookmarksOnly,
        onToggleBookmark = viewModel::toggleBookmark,
        onSaveItem = viewModel::saveItem,
        onDeleteItem = viewModel::deleteItem,
        onOpenPdf = onOpenPdf
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCraftFilterChanged: (Craft?) -> Unit,
    onBookmarksOnlyChanged: (Boolean) -> Unit,
    onToggleBookmark: (LibraryItem) -> Unit,
    onSaveItem: (LibraryItem?, String, Craft, String, String, List<String>, String, String?, String?) -> Unit,
    onDeleteItem: (LibraryItem) -> Unit,
    onOpenPdf: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingItem by remember { mutableStateOf<LibraryItem?>(null) }
    var isAddingItem by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<LibraryItem?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            LibraryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            LibraryUiState.Error -> {
                MessageState(
                    title = stringResource(R.string.library_load_error_title),
                    description = stringResource(R.string.library_load_error_description),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(StitchbookSpacing.extraLarge)
                )
            }

            is LibraryUiState.Content -> {
                LibraryContent(
                    uiState = uiState,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onCraftFilterChanged = onCraftFilterChanged,
                    onBookmarksOnlyChanged = onBookmarksOnlyChanged,
                    onToggleBookmark = onToggleBookmark,
                    onEditItem = { editingItem = it },
                    onDeleteRequested = { deletingItem = it },
                    onOpenPdf = onOpenPdf
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { isAddingItem = true },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_library_item)
                )
            },
            text = { Text(text = stringResource(R.string.add_library_item)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StitchbookSpacing.medium)
        )
    }

    if (isAddingItem) {
        LibraryItemDialog(
            original = null,
            onDismiss = { isAddingItem = false },
            onSave = { title, craft, author, sourceUrl, tags, notes, pdfUri, pdfFileName ->
                onSaveItem(null, title, craft, author, sourceUrl, tags, notes, pdfUri, pdfFileName)
                isAddingItem = false
            }
        )
    }

    editingItem?.let { item ->
        LibraryItemDialog(
            original = item,
            onDismiss = { editingItem = null },
            onSave = { title, craft, author, sourceUrl, tags, notes, pdfUri, pdfFileName ->
                onSaveItem(item, title, craft, author, sourceUrl, tags, notes, pdfUri, pdfFileName)
                editingItem = null
            }
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(text = stringResource(R.string.delete_library_item_title)) },
            text = {
                Text(text = stringResource(R.string.delete_library_item_confirmation, item.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(item)
                        deletingItem = null
                    }
                ) {
                    Text(text = stringResource(R.string.delete_project))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LibraryContent(
    uiState: LibraryUiState.Content,
    onSearchQueryChanged: (String) -> Unit,
    onCraftFilterChanged: (Craft?) -> Unit,
    onBookmarksOnlyChanged: (Boolean) -> Unit,
    onToggleBookmark: (LibraryItem) -> Unit,
    onEditItem: (LibraryItem) -> Unit,
    onDeleteRequested: (LibraryItem) -> Unit,
    onOpenPdf: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = StitchbookSpacing.medium,
            top = StitchbookSpacing.medium,
            end = StitchbookSpacing.medium,
            bottom = 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
    ) {
        item {
            Text(
                text = stringResource(R.string.library_header_title),
                style = MaterialTheme.typography.headlineMedium
            )
            QuietText(text = stringResource(R.string.library_header_subtitle))
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        item {
            OutlinedTextField(
                value = uiState.filter.searchQuery,
                onValueChange = onSearchQueryChanged,
                singleLine = true,
                label = { Text(text = stringResource(R.string.library_search_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CraftFilterDropdown(
                    selected = uiState.filter.craftFilter,
                    onSelected = onCraftFilterChanged,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState.filter.bookmarksOnly,
                    onClick = { onBookmarksOnlyChanged(!uiState.filter.bookmarksOnly) },
                    label = { Text(text = stringResource(R.string.library_bookmarks_only)) }
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        if (uiState.items.isEmpty()) {
            item {
                MessageState(
                    title = stringResource(R.string.library_empty_title),
                    description = stringResource(R.string.library_empty_description)
                )
            }
        } else {
            items(items = uiState.items, key = { it.id }) { libraryItem ->
                LibraryItemCard(
                    item = libraryItem,
                    onToggleBookmark = { onToggleBookmark(libraryItem) },
                    onEdit = { onEditItem(libraryItem) },
                    onDelete = { onDeleteRequested(libraryItem) },
                    onOpenPdf = { onOpenPdf(libraryItem.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraftFilterDropdown(
    selected: Craft?,
    onSelected: (Craft?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.let { stringResource(it.labelResource()) }
                ?: stringResource(R.string.library_filter_all_crafts),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.library_filter_all_crafts)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            Craft.entries.forEach { craft ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(craft.labelResource())) },
                    onClick = {
                        onSelected(craft)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: LibraryItem,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPdf: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabelPill(
                    text = stringResource(item.craft.labelResource()),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (item.bookmarked) {
                            Icons.Outlined.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = stringResource(R.string.library_toggle_bookmark),
                        tint = if (item.bookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
            Text(
                text = item.title,
                style = MaterialTheme.typography.cardTitle,
                fontWeight = FontWeight.SemiBold
            )

            item.author?.let { author ->
                QuietText(text = stringResource(R.string.library_item_author, author))
            }

            if (item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)) {
                    item.tags.forEach { tag ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            item.notes?.let { notes ->
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(StitchbookSpacing.small)
                    )
                }
            }

            if (item.pdfUri != null) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = onOpenPdf
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(StitchbookSpacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = item.pdfFileName ?: stringResource(R.string.library_view_pdf_action),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Text(
                            text = stringResource(R.string.library_view_pdf_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.sourceUrl != null) {
                    Text(
                        text = stringResource(R.string.library_item_source_link),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Spacer(modifier = Modifier)
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.edit_library_item)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete_library_item)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItemDialog(
    original: LibraryItem?,
    onDismiss: () -> Unit,
    onSave: (String, Craft, String, String, List<String>, String, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var craft by remember { mutableStateOf(original?.craft ?: Craft.KNITTING) }
    var author by remember { mutableStateOf(original?.author.orEmpty()) }
    var sourceUrl by remember { mutableStateOf(original?.sourceUrl.orEmpty()) }
    var tagsText by remember { mutableStateOf(original?.tags?.joinToString(", ").orEmpty()) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var titleIsBlank by remember { mutableStateOf(false) }
    var pdfUri by remember { mutableStateOf(original?.pdfUri) }
    var pdfFileName by remember { mutableStateOf(original?.pdfFileName) }

    val context = LocalContext.current
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pdfUri = uri.toString()
            pdfFileName = queryDisplayName(context, uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (original == null) {
                    stringResource(R.string.library_form_title_add)
                } else {
                    stringResource(R.string.library_form_title_edit)
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleIsBlank = false
                    },
                    singleLine = true,
                    isError = titleIsBlank,
                    label = { Text(text = stringResource(R.string.library_field_title)) },
                    supportingText = if (titleIsBlank) {
                        { Text(text = stringResource(R.string.library_field_title_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                CraftDropdown(selected = craft, onSelected = { craft = it })
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.library_field_author)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.library_field_source_url)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.library_field_tags)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    minLines = 3,
                    label = { Text(text = stringResource(R.string.library_field_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                if (pdfUri == null) {
                    OutlinedButton(
                        onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.library_attach_pdf_action),
                            modifier = Modifier.padding(start = StitchbookSpacing.small)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = pdfFileName.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        TextButton(onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) }) {
                            Text(text = stringResource(R.string.library_change_pdf_action))
                        }
                        IconButton(
                            onClick = {
                                pdfUri = null
                                pdfFileName = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.library_remove_pdf_action)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleIsBlank = true
                    } else {
                        onSave(
                            title,
                            craft,
                            author,
                            sourceUrl,
                            tagsText.split(","),
                            notes,
                            pdfUri,
                            pdfFileName
                        )
                    }
                }
            ) {
                Text(text = stringResource(R.string.save_library_item))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraftDropdown(
    selected: Craft,
    onSelected: (Craft) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelResource()),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.project_craft_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Craft.entries.forEach { craft ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(craft.labelResource())) },
                    onClick = {
                        onSelected(craft)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    StitchbookTheme {
        LibraryScreen(
            uiState = LibraryUiState.Content(
                items = listOf(
                    LibraryItem(
                        id = "preview",
                        title = "Raglan Construction Guide",
                        craft = Craft.KNITTING,
                        author = "Elizabeth Zimmermann",
                        sourceUrl = "https://example.com",
                        tags = listOf("raglan", "construction"),
                        notes = "Great reference for top-down raglan increases.",
                        bookmarked = true,
                        createdAt = 0,
                        updatedAt = 0
                    )
                ),
                filter = LibraryFilterState(),
                hasAnyItems = true
            ),
            onSearchQueryChanged = {},
            onCraftFilterChanged = {},
            onBookmarksOnlyChanged = {},
            onToggleBookmark = {},
            onSaveItem = { _, _, _, _, _, _, _, _, _ -> },
            onDeleteItem = {},
            onOpenPdf = {}
        )
    }
}

/** Best-effort: falls back to null (the card/dialog then just shows the generic "View PDF" label) rather than failing the attach. */
private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    } catch (_: Exception) {
        null
    }
}
