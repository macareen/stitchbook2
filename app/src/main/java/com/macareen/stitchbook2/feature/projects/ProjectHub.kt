package com.macareen.stitchbook2.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectConnections
import com.macareen.stitchbook2.domain.model.ProjectNextStep
import com.macareen.stitchbook2.domain.model.ProjectNode
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.suggestedToolCategories
import com.macareen.stitchbook2.feature.tools.labelResource
import com.macareen.stitchbook2.ui.components.ChoiceChipRow
import com.macareen.stitchbook2.ui.components.HubNode
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.components.formatDuration
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.textSecondary

/** Hub data that lives beside the main detail state. */
data class ProjectHubState(
    val connections: ProjectConnections = ProjectConnections(),
    val counters: List<Counter> = emptyList(),
    val toolbox: List<ToolItem> = emptyList()
)

/** Edits the hub's sheets can make; each writes to the shared toolbox or counters. */
data class ProjectHubActions(
    val onIncrementCounter: (Counter) -> Unit = {},
    val onDecrementCounter: (Counter) -> Unit = {},
    val onAddCounter: (name: String, unitLabel: String, goal: Int?) -> Unit = { _, _, _ -> },
    val onAssignTool: (ToolItem) -> Unit = {},
    val onAddNewTool: (name: String, category: ToolCategory) -> Unit = { _, _ -> }
)

/** Which bottom sheet a node opened. */
enum class HubSheet { GUIDES, TOOLS, COUNTERS }

/**
 * The project's name with one quiet line of craft, type, and status. Every
 * occasional action lives behind one overflow menu so the screen leads
 * with the work, not with buttons.
 */
@Composable
internal fun ProjectHubHeader(
    project: Project,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onShareCard: () -> Unit,
    onExportJson: () -> Unit,
    onExportMarkdown: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = project.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = listOf(
                    stringResource(project.craft.labelResource()),
                    project.typeDisplayLabel(),
                    stringResource(project.status.labelResource())
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.textSecondary
            )
        }
        IconButton(onClick = { menuOpen = true }, enabled = !isDeleting) {
            Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.project_more_actions))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(
                R.string.edit_project to onEdit,
                R.string.card_title to onShareCard,
                R.string.project_export_json to onExportJson,
                R.string.project_export_markdown to onExportMarkdown
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(label)) },
                    onClick = {
                        menuOpen = false
                        action()
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.delete_project), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
    project.description?.takeIf { it.isNotBlank() }?.let {
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Text(text = it, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The single obvious next action for this project. */
@Composable
internal fun NextStepRow(
    step: ProjectNextStep,
    onOpenGuide: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onAddGuide: () -> Unit
) {
    val (label, action) = when (step) {
        is ProjectNextStep.ContinueGuide ->
            stringResource(R.string.project_next_continue, step.guideName) to { onOpenGuide(step.guideId) }
        is ProjectNextStep.StartGuide ->
            stringResource(R.string.project_next_start, step.guideName) to { onOpenGuide(step.guideId) }
        is ProjectNextStep.FinishDraft ->
            stringResource(R.string.project_next_finish_draft, step.guideName) to { onEditDraft(step.guideId) }
        ProjectNextStep.AddGuide -> stringResource(R.string.project_next_add_guide) to onAddGuide
    }
    if (step is ProjectNextStep.AddGuide || step is ProjectNextStep.FinishDraft) {
        SecondaryActionButton(text = label, onClick = action, modifier = Modifier.fillMaxWidth())
    } else {
        PrimaryActionButton(text = label, onClick = action, modifier = Modifier.fillMaxWidth())
    }
}

/** The nodes around the project, built from live counts. */
@Composable
internal fun hubNodes(connections: ProjectConnections, onNodeClick: (ProjectNode) -> Unit): List<HubNode> =
    ProjectNode.entries.map { node ->
        val count = connections.count(node)
        val label = stringResource(node.labelResource())
        val detail = when (node) {
            ProjectNode.TIME -> if (connections.workedMillis > 0) formatDuration(connections.workedMillis) else null
            else -> if (count > 0) count.toString() else null
        }
        HubNode(
            label = label,
            icon = node.icon(),
            detail = detail,
            accessibilityLabel = if (detail == null) {
                stringResource(R.string.project_node_empty_description, label)
            } else {
                stringResource(R.string.project_node_description, label, detail)
            },
            isEmpty = detail == null,
            onClick = { onNodeClick(node) }
        )
    }

private fun ProjectNode.labelResource(): Int = when (this) {
    ProjectNode.GUIDES -> R.string.project_node_guides
    ProjectNode.PATTERNS -> R.string.project_node_patterns
    ProjectNode.YARN -> R.string.project_node_yarn
    ProjectNode.TOOLS -> R.string.project_node_tools
    ProjectNode.COUNTERS -> R.string.project_node_counters
    ProjectNode.JOURNAL -> R.string.project_node_journal
    ProjectNode.TIME -> R.string.project_node_time
}

private fun ProjectNode.icon() = when (this) {
    ProjectNode.GUIDES -> Icons.Outlined.Description
    ProjectNode.PATTERNS -> Icons.AutoMirrored.Outlined.MenuBook
    ProjectNode.YARN -> Icons.Outlined.Inventory2
    ProjectNode.TOOLS -> Icons.Outlined.Build
    ProjectNode.COUNTERS -> Icons.Outlined.Tag
    ProjectNode.JOURNAL -> Icons.Outlined.AutoStories
    ProjectNode.TIME -> Icons.Outlined.Timer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HubBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StitchbookSpacing.large)
                .padding(bottom = StitchbookSpacing.large)
                .navigationBarsPadding()
        ) {
            content()
        }
    }
}

/** This project's counters with quick +/-; new ones also show on the Counters screen. */
@Composable
internal fun CountersSheetContent(
    counters: List<Counter>,
    onIncrement: (Counter) -> Unit,
    onDecrement: (Counter) -> Unit,
    onAdd: (String, String, Int?) -> Unit
) {
    Text(text = stringResource(R.string.project_node_counters), style = MaterialTheme.typography.titleLarge)
    if (counters.isEmpty()) QuietText(text = stringResource(R.string.project_counters_empty))
    counters.forEach { counter ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = counter.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = counter.goal?.let { stringResource(R.string.project_counter_value_goal, counter.currentValue, it, counter.unitLabel) }
                        ?: stringResource(R.string.project_counter_value, counter.currentValue, counter.unitLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.textSecondary
                )
            }
            IconButton(onClick = { onDecrement(counter) }, enabled = counter.currentValue > 0) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.project_counter_decrement, counter.name))
            }
            IconButton(onClick = { onIncrement(counter) }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.project_counter_increment, counter.name))
            }
        }
    }
    HorizontalDivider()
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    Text(text = stringResource(R.string.project_counter_new), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
        label = { Text(stringResource(R.string.project_counter_name)) }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
        OutlinedTextField(value = unit, onValueChange = { unit = it }, singleLine = true,
            label = { Text(stringResource(R.string.project_counter_unit)) }, modifier = Modifier.weight(1f))
        OutlinedTextField(value = goal, onValueChange = { goal = it.filter(Char::isDigit) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.project_counter_goal)) }, modifier = Modifier.weight(1f))
    }
    PrimaryActionButton(
        text = stringResource(R.string.project_counter_add),
        enabled = name.isNotBlank(),
        onClick = {
            onAdd(name, unit, goal.toIntOrNull())
            name = ""
            unit = ""
            goal = ""
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Tools linked to this project, plus two ways to add one: link a tool
 * already in the toolbox, or create a new one, which lands in the toolbox
 * too, so the project and the toolbox never disagree.
 */
@Composable
internal fun ToolsSheetContent(
    craft: Craft,
    assigned: List<ToolItem>,
    toolbox: List<ToolItem>,
    onUnassign: (ToolItem) -> Unit,
    onAssign: (ToolItem) -> Unit,
    onAddNew: (String, ToolCategory) -> Unit
) {
    Text(text = stringResource(R.string.project_node_tools), style = MaterialTheme.typography.titleLarge)
    if (assigned.isEmpty()) QuietText(text = stringResource(R.string.project_tools_empty_description))
    assigned.forEach { tool ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = tool.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onUnassign(tool) }) { Text(stringResource(R.string.project_unassign_tool_action)) }
        }
    }
    val assignedIds = assigned.map { it.id }.toSet()
    val available = toolbox.filterNot { it.id in assignedIds }
    if (available.isNotEmpty()) {
        HorizontalDivider()
        Text(text = stringResource(R.string.project_tools_from_toolbox), style = MaterialTheme.typography.titleSmall)
        available.forEach { tool ->
            Surface(onClick = { onAssign(tool) }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = StitchbookSpacing.small)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(StitchbookSpacing.small))
                    Text(text = tool.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    HorizontalDivider()
    var name by remember { mutableStateOf("") }
    val categories = remember(craft) { craft.suggestedToolCategories() }
    var category by remember(craft) { mutableStateOf(categories.first()) }
    Text(text = stringResource(R.string.project_tools_new), style = MaterialTheme.typography.titleSmall)
    QuietText(text = stringResource(R.string.project_tools_new_description))
    ChoiceChipRow(
        options = categories,
        selected = category,
        optionLabel = { stringResource(it.labelResource()) },
        onSelected = { category = it }
    )
    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
        label = { Text(stringResource(R.string.project_tools_new_name)) }, modifier = Modifier.fillMaxWidth())
    PrimaryActionButton(
        text = stringResource(R.string.project_tools_add),
        enabled = name.isNotBlank(),
        onClick = {
            onAddNew(name, category)
            name = ""
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** A short line under the map when the project has nothing connected yet. */
@Composable
internal fun EmptyHubHint() {
    Text(
        text = stringResource(R.string.project_hub_empty_hint),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.textSecondary
    )
}
