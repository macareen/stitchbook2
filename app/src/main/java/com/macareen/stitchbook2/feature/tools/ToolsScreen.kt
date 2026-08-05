package com.macareen.stitchbook2.feature.tools

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
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
import com.macareen.stitchbook2.data.csv.ToolsCsvImportReport
import com.macareen.stitchbook2.data.csv.toolsCsvTemplate
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
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
fun ToolsRoute(viewModel: ToolsViewModel, onBulkCreate: () -> Unit, onManageSets: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()

    ToolsScreen(
        uiState = uiState,
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onCategoryFilterChanged = viewModel::updateCategoryFilter,
        onSaveItem = viewModel::saveItem,
        onDeleteItem = viewModel::deleteItem,
        onBulkCreate = onBulkCreate,
        onManageSets = onManageSets,
        onExportCsv = viewModel::exportCsv,
        onImportCsv = viewModel::importCsv,
        importReport = importReport,
        onDismissImportReport = viewModel::dismissImportReport,
        onLoadAssignedProjectIds = viewModel::loadAssignedProjectIds,
        onSaveProjectAssignments = viewModel::saveProjectAssignments
    )
}

@Composable
fun ToolsScreen(
    uiState: ToolsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryFilterChanged: (ToolCategory?) -> Unit,
    onSaveItem: (ToolItem?, ToolItemFormInput) -> Unit,
    onDeleteItem: (ToolItem) -> Unit,
    onBulkCreate: () -> Unit,
    onManageSets: () -> Unit,
    onExportCsv: (suspend (String) -> Unit) -> Unit,
    onImportCsv: (String) -> Unit,
    importReport: ToolsCsvImportReport?,
    onDismissImportReport: () -> Unit,
    onLoadAssignedProjectIds: suspend (String) -> Set<String>,
    onSaveProjectAssignments: (String, Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var assigningItem by remember { mutableStateOf<ToolItem?>(null) }
    var editingItem by remember { mutableStateOf<ToolItem?>(null) }
    var isAddingItem by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<ToolItem?>(null) }

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
                        OutputStreamWriter(stream).use { it.write(toolsCsvTemplate()) }
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
            ToolsUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            ToolsUiState.Error -> {
                MessageState(
                    title = stringResource(R.string.tools_load_error_title),
                    description = stringResource(R.string.tools_load_error_description),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(StitchbookSpacing.extraLarge)
                )
            }

            is ToolsUiState.Content -> {
                ToolsContent(
                    uiState = uiState,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onCategoryFilterChanged = onCategoryFilterChanged,
                    onEditItem = { editingItem = it },
                    onDeleteRequested = { deletingItem = it },
                    onAssignRequested = { assigningItem = it },
                    onBulkCreate = onBulkCreate,
                    onManageSets = onManageSets,
                    onExportCsvClick = { exportCsvLauncher.launch(toolsCsvFileName()) },
                    onImportCsvClick = {
                        importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                    },
                    onTemplateCsvClick = { templateCsvLauncher.launch(TOOLS_CSV_TEMPLATE_FILE_NAME) }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { isAddingItem = true },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_tool_item)
                )
            },
            text = { Text(text = stringResource(R.string.add_tool_item)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StitchbookSpacing.medium)
        )
    }

    val availableSets = (uiState as? ToolsUiState.Content)?.sets.orEmpty()

    if (isAddingItem) {
        ToolItemDialog(
            original = null,
            availableSets = availableSets,
            onDismiss = { isAddingItem = false },
            onSave = { form ->
                onSaveItem(null, form)
                isAddingItem = false
            }
        )
    }

    editingItem?.let { item ->
        ToolItemDialog(
            original = item,
            availableSets = availableSets,
            onDismiss = { editingItem = null },
            onSave = { form ->
                onSaveItem(item, form)
                editingItem = null
            }
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(text = stringResource(R.string.delete_tool_item_title)) },
            text = {
                Text(text = stringResource(R.string.delete_tool_item_confirmation, item.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(item)
                        deletingItem = null
                    }
                ) {
                    Text(text = stringResource(R.string.delete_tool_item_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    importReport?.let { report -> ToolsCsvImportReportDialog(report = report, onDismiss = onDismissImportReport) }

    assigningItem?.let { item ->
        val availableProjects = (uiState as? ToolsUiState.Content)?.projects.orEmpty()
        AssignToProjectsDialog(
            item = item,
            availableProjects = availableProjects,
            onLoadAssignedProjectIds = onLoadAssignedProjectIds,
            onDismiss = { assigningItem = null },
            onSave = { projectIds ->
                onSaveProjectAssignments(item.id, projectIds)
                assigningItem = null
            }
        )
    }
}

/**
 * A multi-select checklist of every project, initialized from persisted
 * assignments via a one-shot [onLoadAssignedProjectIds] load rather than a
 * live-observed flow -- this dialog's own local checkbox state is the
 * source of truth until Save, exactly like [ToolItemDialog]'s form fields.
 */
@Composable
private fun AssignToProjectsDialog(
    item: ToolItem,
    availableProjects: List<Project>,
    onLoadAssignedProjectIds: suspend (String) -> Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selectedIds by remember(item.id) { mutableStateOf<Set<String>?>(null) }

    LaunchedEffect(item.id) {
        selectedIds = onLoadAssignedProjectIds(item.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.tools_assign_to_projects_title, item.name)) },
        text = {
            val ids = selectedIds
            when {
                ids == null -> CircularProgressIndicator()
                availableProjects.isEmpty() -> QuietText(text = stringResource(R.string.tools_assign_to_projects_no_projects))
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    availableProjects.forEach { project ->
                        val isChecked = project.id in ids
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedIds = if (isChecked) ids - project.id else ids + project.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) ids + project.id else ids - project.id
                                }
                            )
                            Text(text = project.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedIds?.let(onSave) },
                enabled = selectedIds != null
            ) {
                Text(text = stringResource(R.string.save_tool_set))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ToolsCsvImportReportDialog(
    report: ToolsCsvImportReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.tools_csv_import_result_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.tools_csv_import_result_summary,
                        report.importedCount,
                        report.rowErrors.size
                    )
                )
                if (report.hasErrors) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)) {
                        report.rowErrors.take(10).forEach { error ->
                            QuietText(
                                text = stringResource(
                                    R.string.tools_csv_import_row_error,
                                    error.rowNumber,
                                    error.message
                                )
                            )
                        }
                        if (report.rowErrors.size > 10) {
                            QuietText(
                                text = stringResource(R.string.tools_csv_import_more_errors, report.rowErrors.size - 10)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.tools_csv_import_dismiss_action))
            }
        }
    )
}

private fun toolsCsvFileName(): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "stitchbook_tools_$date.csv"
}

private const val TOOLS_CSV_TEMPLATE_FILE_NAME = "stitchbook_tools_template.csv"

@Composable
private fun ToolsContent(
    uiState: ToolsUiState.Content,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryFilterChanged: (ToolCategory?) -> Unit,
    onEditItem: (ToolItem) -> Unit,
    onDeleteRequested: (ToolItem) -> Unit,
    onAssignRequested: (ToolItem) -> Unit,
    onBulkCreate: () -> Unit,
    onManageSets: () -> Unit,
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
                text = stringResource(R.string.tools_header_title),
                style = MaterialTheme.typography.headlineMedium
            )
            QuietText(text = stringResource(R.string.tools_header_subtitle))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                TextButton(onClick = onBulkCreate) {
                    Text(text = stringResource(R.string.tools_bulk_create_link))
                }
                TextButton(onClick = onManageSets) {
                    Text(text = stringResource(R.string.tools_manage_sets_link))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                TextButton(onClick = onExportCsvClick) {
                    Text(text = stringResource(R.string.tools_export_csv_action))
                }
                TextButton(onClick = onImportCsvClick) {
                    Text(text = stringResource(R.string.tools_import_csv_action))
                }
                TextButton(onClick = onTemplateCsvClick) {
                    Text(text = stringResource(R.string.tools_download_csv_template_action))
                }
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            OutlinedTextField(
                value = uiState.filter.searchQuery,
                onValueChange = onSearchQueryChanged,
                singleLine = true,
                label = { Text(text = stringResource(R.string.tools_search_placeholder)) },
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
                    title = stringResource(R.string.tools_empty_title),
                    description = stringResource(R.string.tools_empty_description)
                )
            }
        } else {
            items(items = uiState.items, key = { it.id }) { toolItem ->
                ToolItemCard(
                    item = toolItem,
                    onEdit = { onEditItem(toolItem) },
                    onDelete = { onDeleteRequested(toolItem) },
                    onAssignToProjects = { onAssignRequested(toolItem) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterDropdown(
    selected: ToolCategory?,
    onSelected: (ToolCategory?) -> Unit,
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
                ?: stringResource(R.string.tools_filter_all_categories),
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
                text = { Text(text = stringResource(R.string.tools_filter_all_categories)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            ToolCategory.entries.forEach { category ->
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
private fun ToolItemCard(
    item: ToolItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssignToProjects: () -> Unit
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
                    text = stringResource(R.string.tools_quantity_pill, item.quantity),
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

            val details = listOfNotNull(
                item.material,
                sizeDetail(item),
                cableDetail(item),
                item.connectorFamily,
                item.compatibilityNotes,
                item.storageLocation
            )
            if (details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(StitchbookSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        details.forEach { detail ->
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
                IconButton(onClick = onAssignToProjects) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Assignment,
                        contentDescription = stringResource(R.string.tools_assign_to_projects_action)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.edit_tool_item)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_tool_item)
                    )
                }
            }
        }
    }
}

private fun sizeDetail(item: ToolItem): String? {
    val sizePart = item.sizeLabel ?: item.sizeMetricMm?.let { "${formatNumber(it)} mm" }
    val lengthPart = item.lengthMm?.let { "${formatNumber(it)} mm long" }
    return listOfNotNull(sizePart, lengthPart).joinToString(" · ").ifEmpty { null }
}

private fun cableDetail(item: ToolItem): String? {
    val stated = item.statedCableLengthMm?.let { "${formatNumber(it)} mm cable" }
    val assembled = item.approximateAssembledLengthMm?.let { "~${formatNumber(it)} mm assembled" }
    return listOfNotNull(stated, item.cableLengthDefinition, assembled)
        .joinToString(" · ")
        .ifEmpty { null }
}

@Composable
private fun ToolItemDialog(
    original: ToolItem?,
    availableSets: List<ToolSet>,
    onDismiss: () -> Unit,
    onSave: (ToolItemFormInput) -> Unit
) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var setId by remember { mutableStateOf(original?.setId) }
    var category by remember { mutableStateOf(original?.category ?: ToolCategory.CROCHET_HOOK) }
    var brand by remember { mutableStateOf(original?.brand.orEmpty()) }
    var material by remember { mutableStateOf(original?.material.orEmpty()) }
    var sizeMetricMmText by remember { mutableStateOf(original?.sizeMetricMm?.toString().orEmpty()) }
    var sizeLabel by remember { mutableStateOf(original?.sizeLabel.orEmpty()) }
    var lengthMmText by remember { mutableStateOf(original?.lengthMm?.toString().orEmpty()) }
    var statedCableLengthMmText by remember {
        mutableStateOf(original?.statedCableLengthMm?.toString().orEmpty())
    }
    var cableLengthDefinition by remember { mutableStateOf(original?.cableLengthDefinition.orEmpty()) }
    var approximateAssembledLengthMmText by remember {
        mutableStateOf(original?.approximateAssembledLengthMm?.toString().orEmpty())
    }
    var connectorFamily by remember { mutableStateOf(original?.connectorFamily.orEmpty()) }
    var compatibilityNotes by remember { mutableStateOf(original?.compatibilityNotes.orEmpty()) }
    var quantityText by remember { mutableStateOf((original?.quantity ?: 1).toString()) }
    var storageLocation by remember { mutableStateOf(original?.storageLocation.orEmpty()) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var nameIsBlank by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (original == null) {
                    stringResource(R.string.tools_form_title_add)
                } else {
                    stringResource(R.string.tools_form_title_edit)
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
                    label = { Text(text = stringResource(R.string.tools_field_name)) },
                    supportingText = if (nameIsBlank) {
                        { Text(text = stringResource(R.string.tools_field_name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                CategoryDropdown(selected = category, onSelected = { category = it })
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                ToolSetDropdown(
                    availableSets = availableSets,
                    selectedSetId = setId,
                    onSelected = { setId = it }
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_field_brand)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(text = stringResource(R.string.tools_field_quantity)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = material,
                    onValueChange = { material = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.tools_field_material)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (category.usesSizeFields()) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                        OutlinedTextField(
                            value = sizeLabel,
                            onValueChange = { sizeLabel = it },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.tools_field_size_label)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sizeMetricMmText,
                            onValueChange = { sizeMetricMmText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text(text = stringResource(R.string.tools_field_size_metric_mm)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (category.usesLengthField()) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    OutlinedTextField(
                        value = lengthMmText,
                        onValueChange = { lengthMmText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(text = stringResource(R.string.tools_field_length_mm)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (category.usesCableFields()) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                        OutlinedTextField(
                            value = statedCableLengthMmText,
                            onValueChange = { statedCableLengthMmText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = {
                                Text(text = stringResource(R.string.tools_field_stated_cable_length_mm))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = approximateAssembledLengthMmText,
                            onValueChange = { approximateAssembledLengthMmText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = {
                                Text(text = stringResource(R.string.tools_field_approximate_assembled_length_mm))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    OutlinedTextField(
                        value = cableLengthDefinition,
                        onValueChange = { cableLengthDefinition = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_field_cable_length_definition)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (category.usesConnectorFields()) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    OutlinedTextField(
                        value = connectorFamily,
                        onValueChange = { connectorFamily = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_field_connector_family)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    OutlinedTextField(
                        value = compatibilityNotes,
                        onValueChange = { compatibilityNotes = it },
                        label = { Text(text = stringResource(R.string.tools_field_compatibility_notes)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = storageLocation,
                    onValueChange = { storageLocation = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.tools_field_storage_location)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    minLines = 3,
                    label = { Text(text = stringResource(R.string.tools_field_notes)) },
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
                            ToolItemFormInput(
                                name = name,
                                category = category,
                                brand = brand,
                                material = material,
                                sizeMetricMmText = sizeMetricMmText,
                                sizeLabel = sizeLabel,
                                lengthMmText = lengthMmText,
                                statedCableLengthMmText = statedCableLengthMmText,
                                cableLengthDefinition = cableLengthDefinition,
                                approximateAssembledLengthMmText = approximateAssembledLengthMmText,
                                connectorFamily = connectorFamily,
                                compatibilityNotes = compatibilityNotes,
                                quantityText = quantityText,
                                storageLocation = storageLocation,
                                notes = notes,
                                setId = setId
                            )
                        )
                    }
                }
            ) {
                Text(text = stringResource(R.string.save_tool_item))
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
    selected: ToolCategory,
    onSelected: (ToolCategory) -> Unit
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
            label = { Text(text = stringResource(R.string.tools_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ToolCategory.entries.forEach { category ->
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

/** Assigns/reassigns an item to one of [availableSets], or to no set at all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolSetDropdown(
    availableSets: List<ToolSet>,
    selectedSetId: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSet = availableSets.firstOrNull { it.id == selectedSetId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedSet?.name ?: stringResource(R.string.tools_set_none),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.tools_field_set)) },
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
                text = { Text(text = stringResource(R.string.tools_set_none)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            availableSets.forEach { set ->
                DropdownMenuItem(
                    text = { Text(text = set.name) },
                    onClick = {
                        onSelected(set.id)
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
            imageVector = Icons.Outlined.Build,
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

private fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolsScreenPreview() {
    StitchbookTheme {
        ToolsScreen(
            uiState = ToolsUiState.Content(
                items = listOf(
                    ToolItem(
                        id = "preview",
                        name = "US 7 interchangeable tip",
                        category = ToolCategory.INTERCHANGEABLE_TIP,
                        brand = "ChiaoGoo",
                        material = "Stainless steel",
                        sizeMetricMm = 4.5,
                        sizeLabel = "US 7",
                        lengthMm = 127.0,
                        statedCableLengthMm = null,
                        cableLengthDefinition = null,
                        approximateAssembledLengthMm = null,
                        connectorFamily = "ChiaoGoo Twist",
                        compatibilityNotes = null,
                        quantity = 2,
                        storageLocation = "Tip case, slot 7",
                        notes = "Slightly bent, still usable.",
                        setId = null,
                        createdAt = 0,
                        updatedAt = 0
                    )
                ),
                filter = ToolFilterState(),
                hasAnyItems = true,
                sets = emptyList(),
                projects = emptyList()
            ),
            onSearchQueryChanged = {},
            onCategoryFilterChanged = {},
            onSaveItem = { _, _ -> },
            onDeleteItem = {},
            onBulkCreate = {},
            onManageSets = {},
            onExportCsv = {},
            onImportCsv = {},
            importReport = null,
            onDismissImportReport = {},
            onLoadAssignedProjectIds = { emptySet() },
            onSaveProjectAssignments = { _, _ -> }
        )
    }
}
