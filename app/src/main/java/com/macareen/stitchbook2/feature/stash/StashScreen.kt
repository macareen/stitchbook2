package com.macareen.stitchbook2.feature.stash

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.data.csv.StashCsvImportReport
import com.macareen.stitchbook2.data.csv.stashCsvTemplate
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StashRoute(viewModel: StashViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()

    StashScreen(
        uiState = uiState,
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onCategoryFilterChanged = viewModel::updateCategoryFilter,
        onSaveItem = viewModel::saveItem,
        onDeleteItem = viewModel::deleteItem,
        onExportCsv = viewModel::exportCsv,
        onImportCsv = viewModel::importCsv,
        importReport = importReport,
        onDismissImportReport = viewModel::dismissImportReport
    )
}

@Composable
fun StashScreen(
    uiState: StashUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryFilterChanged: (StashCategory?) -> Unit,
    onSaveItem: (
        StashItem?, String, StashCategory, String, String, String, String, String,
        Double, String, Double?, String
    ) -> Unit,
    onDeleteItem: (StashItem) -> Unit,
    onExportCsv: (suspend (String) -> Unit) -> Unit,
    onImportCsv: (String) -> Unit,
    importReport: StashCsvImportReport?,
    onDismissImportReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingItem by remember { mutableStateOf<StashItem?>(null) }
    var isAddingItem by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<StashItem?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportCsv { csv ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream).use { it.write(csv) }
                    }
                }
            }
        }
    }

    val templateCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream).use { it.write(stashCsvTemplate()) }
                    }
                }
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                }
                if (text != null) {
                    onImportCsv(text)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            StashUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            StashUiState.Error -> {
                MessageState(
                    title = stringResource(R.string.stash_load_error_title),
                    description = stringResource(R.string.stash_load_error_description),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(StitchbookSpacing.extraLarge)
                )
            }

            is StashUiState.Content -> {
                StashContent(
                    uiState = uiState,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onCategoryFilterChanged = onCategoryFilterChanged,
                    onEditItem = { editingItem = it },
                    onDeleteRequested = { deletingItem = it },
                    onExportCsvClick = { exportCsvLauncher.launch(stashCsvFileName()) },
                    onImportCsvClick = { importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                    onTemplateCsvClick = { templateCsvLauncher.launch(STASH_CSV_TEMPLATE_FILE_NAME) }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { isAddingItem = true },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_stash_item)
                )
            },
            text = { Text(text = stringResource(R.string.add_stash_item)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StitchbookSpacing.medium)
        )
    }

    if (isAddingItem) {
        StashItemDialog(
            original = null,
            onDismiss = { isAddingItem = false },
            onSave = { name, category, brand, colorway, dyeLot, weight, fiber, quantity, unit, yardage, notes ->
                onSaveItem(null, name, category, brand, colorway, dyeLot, weight, fiber, quantity, unit, yardage, notes)
                isAddingItem = false
            }
        )
    }

    editingItem?.let { item ->
        StashItemDialog(
            original = item,
            onDismiss = { editingItem = null },
            onSave = { name, category, brand, colorway, dyeLot, weight, fiber, quantity, unit, yardage, notes ->
                onSaveItem(item, name, category, brand, colorway, dyeLot, weight, fiber, quantity, unit, yardage, notes)
                editingItem = null
            }
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(text = stringResource(R.string.delete_stash_item_title)) },
            text = {
                Text(text = stringResource(R.string.delete_stash_item_confirmation, item.name))
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

    importReport?.let { report -> CsvImportReportDialog(report = report, onDismiss = onDismissImportReport) }
}

@Composable
private fun CsvImportReportDialog(
    report: StashCsvImportReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.stash_csv_import_result_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.stash_csv_import_result_summary, report.importedCount, report.rowErrors.size)
                )
                if (report.hasErrors) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)) {
                        report.rowErrors.take(10).forEach { error ->
                            QuietText(
                                text = stringResource(
                                    R.string.stash_csv_import_row_error,
                                    error.rowNumber,
                                    error.message
                                )
                            )
                        }
                        if (report.rowErrors.size > 10) {
                            QuietText(
                                text = stringResource(R.string.stash_csv_import_more_errors, report.rowErrors.size - 10)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.stash_csv_import_dismiss_action))
            }
        }
    )
}

private fun stashCsvFileName(): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "stitchbook_stash_$date.csv"
}

private const val STASH_CSV_TEMPLATE_FILE_NAME = "stitchbook_stash_template.csv"

@Composable
private fun StashContent(
    uiState: StashUiState.Content,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryFilterChanged: (StashCategory?) -> Unit,
    onEditItem: (StashItem) -> Unit,
    onDeleteRequested: (StashItem) -> Unit,
    onExportCsvClick: () -> Unit,
    onImportCsvClick: () -> Unit,
    onTemplateCsvClick: () -> Unit
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
                text = stringResource(R.string.stash_header_title),
                style = MaterialTheme.typography.headlineMedium
            )
            QuietText(text = stringResource(R.string.stash_header_subtitle))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                TextButton(onClick = onExportCsvClick) {
                    Text(text = stringResource(R.string.stash_export_csv_action))
                }
                TextButton(onClick = onImportCsvClick) {
                    Text(text = stringResource(R.string.stash_import_csv_action))
                }
                TextButton(onClick = onTemplateCsvClick) {
                    Text(text = stringResource(R.string.stash_download_csv_template_action))
                }
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            OutlinedTextField(
                value = uiState.filter.searchQuery,
                onValueChange = onSearchQueryChanged,
                singleLine = true,
                label = { Text(text = stringResource(R.string.stash_search_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            CategoryFilterDropdown(
                selected = uiState.filter.categoryFilter,
                onSelected = onCategoryFilterChanged,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        if (uiState.items.isEmpty()) {
            item {
                MessageState(
                    title = stringResource(R.string.stash_empty_title),
                    description = stringResource(R.string.stash_empty_description)
                )
            }
        } else {
            items(items = uiState.items, key = { it.id }) { stashItem ->
                StashItemCard(
                    item = stashItem,
                    onEdit = { onEditItem(stashItem) },
                    onDelete = { onDeleteRequested(stashItem) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterDropdown(
    selected: StashCategory?,
    onSelected: (StashCategory?) -> Unit,
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
                ?: stringResource(R.string.stash_filter_all_categories),
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
                text = { Text(text = stringResource(R.string.stash_filter_all_categories)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            StashCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(category.labelResource())) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StashItemCard(
    item: StashItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                    text = stringResource(item.category.labelResource()),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                LabelPill(
                    text = "${formatQuantity(item.quantity)} ${item.unitLabel}",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = item.name,
                style = MaterialTheme.typography.cardTitle,
                fontWeight = FontWeight.SemiBold
            )
            item.brand?.let { QuietText(text = it) }

            val yarnDetails = listOfNotNull(
                item.colorway?.let { colorway ->
                    if (item.dyeLot != null) "$colorway (${item.dyeLot})" else colorway
                },
                item.weightCategory,
                item.fiberContent,
                item.yardagePerUnit?.let {
                    stringResource(R.string.stash_yardage_per_unit, formatQuantity(it))
                }
            )
            if (yarnDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(StitchbookSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        yarnDetails.forEach { detail ->
                            QuietText(text = detail)
                        }
                    }
                }
            }

            item.notes?.let { notes ->
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.edit_stash_item)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_stash_item)
                    )
                }
            }
        }
    }
}

@Composable
private fun StashItemDialog(
    original: StashItem?,
    onDismiss: () -> Unit,
    onSave: (
        String, StashCategory, String, String, String, String, String,
        Double, String, Double?, String
    ) -> Unit
) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var category by remember { mutableStateOf(original?.category ?: StashCategory.YARN) }
    var brand by remember { mutableStateOf(original?.brand.orEmpty()) }
    var colorway by remember { mutableStateOf(original?.colorway.orEmpty()) }
    var dyeLot by remember { mutableStateOf(original?.dyeLot.orEmpty()) }
    var weightCategory by remember { mutableStateOf(original?.weightCategory.orEmpty()) }
    var fiberContent by remember { mutableStateOf(original?.fiberContent.orEmpty()) }
    var quantityText by remember { mutableStateOf(original?.quantity?.toString() ?: "1") }
    var unitLabel by remember { mutableStateOf(original?.unitLabel ?: "skeins") }
    var yardageText by remember { mutableStateOf(original?.yardagePerUnit?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var nameIsBlank by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (original == null) {
                    stringResource(R.string.stash_form_title_add)
                } else {
                    stringResource(R.string.stash_form_title_edit)
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameIsBlank = false
                    },
                    singleLine = true,
                    isError = nameIsBlank,
                    label = { Text(text = stringResource(R.string.stash_field_name)) },
                    supportingText = if (nameIsBlank) {
                        { Text(text = stringResource(R.string.stash_field_name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                CategoryDropdown(selected = category, onSelected = { category = it })
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.stash_field_brand)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(text = stringResource(R.string.stash_field_quantity)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.stash_field_unit_label)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (category == StashCategory.YARN) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                        OutlinedTextField(
                            value = colorway,
                            onValueChange = { colorway = it },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.stash_field_colorway)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dyeLot,
                            onValueChange = { dyeLot = it },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.stash_field_dye_lot)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                        OutlinedTextField(
                            value = weightCategory,
                            onValueChange = { weightCategory = it },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.stash_field_weight_category)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = yardageText,
                            onValueChange = { yardageText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text(text = stringResource(R.string.stash_field_yardage_per_unit)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    OutlinedTextField(
                        value = fiberContent,
                        onValueChange = { fiberContent = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.stash_field_fiber_content)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    minLines = 3,
                    label = { Text(text = stringResource(R.string.stash_field_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameIsBlank = true
                    } else {
                        onSave(
                            name,
                            category,
                            brand,
                            colorway,
                            dyeLot,
                            weightCategory,
                            fiberContent,
                            quantityText.toDoubleOrNull() ?: 1.0,
                            unitLabel,
                            yardageText.toDoubleOrNull(),
                            notes
                        )
                    }
                }
            ) {
                Text(text = stringResource(R.string.save_stash_item))
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
private fun CategoryDropdown(
    selected: StashCategory,
    onSelected: (StashCategory) -> Unit
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
            label = { Text(text = stringResource(R.string.stash_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            StashCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(category.labelResource())) },
                    onClick = {
                        onSelected(category)
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
            imageVector = Icons.Outlined.Inventory2,
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

private fun formatQuantity(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

@Preview(showBackground = true)
@Composable
private fun StashScreenPreview() {
    StitchbookTheme {
        StashScreen(
            uiState = StashUiState.Content(
                items = listOf(
                    StashItem(
                        id = "preview",
                        name = "Cascade 220",
                        category = StashCategory.YARN,
                        brand = "Cascade Yarns",
                        colorway = "Ivory",
                        dyeLot = "12345",
                        weightCategory = "Worsted",
                        fiberContent = "100% Peruvian Highland Wool",
                        quantity = 6.0,
                        unitLabel = "skeins",
                        yardagePerUnit = 220.0,
                        notes = "Reserved for the cardigan body.",
                        createdAt = 0,
                        updatedAt = 0
                    )
                ),
                filter = StashFilterState(),
                hasAnyItems = true
            ),
            onSearchQueryChanged = {},
            onCategoryFilterChanged = {},
            onSaveItem = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
            onDeleteItem = {},
            onExportCsv = {},
            onImportCsv = {},
            importReport = null,
            onDismissImportReport = {}
        )
    }
}
