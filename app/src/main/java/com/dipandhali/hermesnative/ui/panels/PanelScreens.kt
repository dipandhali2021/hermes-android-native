package com.dipandhali.hermesnative.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.model.InsightsResponse
import java.util.Locale

/**
 * The eight side panels, rendered from one state object.
 *
 * They are one composable rather than eight because the differences are genuinely
 * small — a title, a body shape, and whether the list is grouped. Splitting them
 * would duplicate the loading/empty/error handling eight times, which is where the
 * inconsistencies would come from.
 */
@Composable
fun PanelScreen(
    kind: PanelKind,
    onBack: () -> Unit,
    viewModel: PanelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()

    LaunchedEffect(kind) { viewModel.load(kind) }

    PanelScaffold(
        title = kind.title,
        onBack = onBack,
        onRefresh = { viewModel.load(kind, force = true) },
    ) { contentModifier ->
        when {
            state.isLoading && state.isPanelEmpty -> PanelLoading(contentModifier)

            state.error != null -> PanelEmpty(
                title = "Couldn't load ${kind.title.lowercase()}",
                reason = state.error!!,
                onRetry = { viewModel.load(kind, force = true) },
                modifier = contentModifier,
            )

            else -> when (kind) {
                PanelKind.SKILLS -> SkillsPanel(state, contentModifier)
                PanelKind.TASKS -> TasksPanel(state, contentModifier)
                PanelKind.MEMORY -> MemoryPanel(state, contentModifier)
                PanelKind.SPACES -> SpacesPanel(state, contentModifier)
                PanelKind.PROFILES -> ProfilesPanel(state, contentModifier)
                PanelKind.TODOS -> TodosPanel(todos, contentModifier)
                PanelKind.INSIGHTS -> InsightsPanel(state, contentModifier)
                PanelKind.LOGS -> LogsPanel(state, contentModifier)
            }
        }
    }
}

@Composable
private fun SkillsPanel(state: PanelsUiState, modifier: Modifier) {
    if (state.skills.isEmpty()) {
        PanelEmpty(
            title = "No skills installed",
            reason = state.unavailable ?: "This server has no skills in its skills/ directory.",
            modifier = modifier,
        )
        return
    }

    // Grouped by category, matching how the desktop panel renders them: a flat
    // list of sixty skills is unreadable.
    val grouped = state.skills
        .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Uncategorised" }
        .toSortedMap()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        grouped.forEach { (category, skills) ->
            item(key = "cat-$category") {
                Text(
                    text = category.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(skills, key = { it.name ?: it.hashCode().toString() }) { skill ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = skill.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (skill.disabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    supportingContent = skill.description
                        ?.takeIf { it.isNotBlank() }
                        ?.let { description ->
                            {
                                Text(
                                    text = description,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    trailingContent = if (skill.disabled) {
                        {
                            Text(
                                text = "off",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun TasksPanel(state: PanelsUiState, modifier: Modifier) {
    if (state.jobs.isEmpty()) {
        // "Unavailable" here is genuinely different from "you have no jobs": a
        // split-container install may not ship the agent's cron package at all, and
        // telling that user to create a task would be wrong.
        PanelEmpty(
            title = if (state.unavailable != null) "Tasks unavailable" else "No scheduled jobs",
            reason = state.unavailable ?: "Nothing is scheduled on this server yet.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(state.jobs, key = { it.id ?: it.hashCode().toString() }) { job ->
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = if (job.isPaused) {
                            Icons.Rounded.PauseCircle
                        } else {
                            Icons.Rounded.CheckCircle
                        },
                        contentDescription = if (job.isPaused) "Paused" else "Active",
                        tint = if (job.isPaused) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                },
                headlineContent = { Text(job.label) },
                supportingContent = {
                    Column {
                        job.schedule?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        job.prompt?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun MemoryPanel(state: PanelsUiState, modifier: Modifier) {
    if (state.memory.isEmpty()) {
        PanelEmpty(
            title = "Nothing in memory",
            reason = state.unavailable ?: "This server's memory is empty.",
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.memory.forEach { (section, body) ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = section,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(text = body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SpacesPanel(state: PanelsUiState, modifier: Modifier) {
    if (state.workspaces.isEmpty()) {
        PanelEmpty(
            title = "No spaces",
            reason = state.unavailable ?: "No workspaces are configured on this server.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(state.workspaces, key = { it.path ?: it.label }) { space ->
            ListItem(
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .padding(0.dp),
                    ) {
                        Surface(
                            color = if (space.active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                },
                headlineContent = { Text(space.label) },
                supportingContent = {
                    Column {
                        space.path?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (space.active) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfilesPanel(state: PanelsUiState, modifier: Modifier) {
    if (state.profiles.isEmpty()) {
        PanelEmpty(
            title = "No profiles",
            reason = state.unavailable ?: "No agent profiles are configured on this server.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(state.profiles, key = { it.label }) { profile ->
            ListItem(
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(profile.label)
                        if (profile.active) {
                            AssistChip(onClick = {}, label = { Text("Active") })
                        }
                    }
                },
                supportingContent = {
                    Column {
                        val model = listOfNotNull(profile.model, profile.provider)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (model.isNotBlank()) {
                            Text(model, style = MaterialTheme.typography.bodySmall)
                        }
                        profile.enabledSkillCount?.let { enabled ->
                            val total = profile.skillCount ?: enabled
                            Text(
                                text = "$enabled of $total skills enabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun TodosPanel(
    items: List<com.dipandhali.hermesnative.core.chat.TodoItem>,
    modifier: Modifier,
) {
    if (items.isEmpty()) {
        PanelEmpty(
            title = "No task list",
            reason = "A task list appears here while a run reports one.",
            modifier = modifier,
        )
        return
    }

    val done = items.count { it.isDone }

    Column(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$done of ${items.size} complete",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { done.toFloat() / items.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(items, key = { it.id ?: it.label }) { todo ->
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = if (todo.isDone) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = if (todo.isDone) "Done" else "Not done",
                            tint = if (todo.isDone) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    headlineContent = {
                        Text(
                            text = todo.label,
                            color = if (todo.isDone) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun InsightsPanel(state: PanelsUiState, modifier: Modifier) {
    val insights: InsightsResponse = state.insights ?: InsightsResponse()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A compact stat row rather than a chart: on a phone the useful question is
        // "roughly what am I spending", and a bar chart of one number is noise.
        val stats = buildList {
            insights.totalSessions?.let { add("Sessions" to it.toString()) }
            insights.totalMessages?.let { add("Messages" to it.toString()) }
            insights.inputTokens?.let { add("Tokens in" to compactNumber(it)) }
            insights.outputTokens?.let { add("Tokens out" to compactNumber(it)) }
            insights.estimatedCost?.let {
                add("Est. cost" to String.format(Locale.ROOT, "$%.4f", it))
            }
            insights.activeDays?.let { add("Active days" to it.toString()) }
        }

        if (stats.isEmpty()) {
            PanelEmpty(
                title = "No usage yet",
                reason = "Insights appear once this server has handled some runs.",
            )
            return@Column
        }

        stats.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (label, value) ->
                    StatTile(label = label, value = value, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }

        insights.models?.takeIf { it.isNotEmpty() }?.let { models ->
            Text(
                text = "Models",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            models.forEach { model ->
                ListItem(
                    headlineContent = { Text(model.model ?: "Unknown") },
                    supportingContent = {
                        Text(
                            buildString {
                                model.requestCount?.let { append("$it requests") }
                                model.errorCount?.takeIf { it > 0 }?.let { append(" · $it errors") }
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogsPanel(state: PanelsUiState, modifier: Modifier) {
    val text = state.logText
    if (text.isNullOrBlank()) {
        PanelEmpty(
            title = "No log output",
            reason = state.unavailable ?: "This server produced no log lines.",
            modifier = modifier,
        )
        return
    }

    // Monospace and horizontally scrollable, because log lines are wide and
    // wrapping them destroys the shape that makes them readable.
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

private val PanelsUiState.isPanelEmpty: Boolean
    get() = skills.isEmpty() && jobs.isEmpty() && memory.isEmpty() && workspaces.isEmpty() &&
        profiles.isEmpty() && insights == null && logText == null
