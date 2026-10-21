package com.dipandhali.hermesnative.ui.kanban.components

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

private val HelpStatuses = listOf(
    KanbanStatus.TRIAGE,
    KanbanStatus.TODO,
    KanbanStatus.SCHEDULED,
    KanbanStatus.READY,
    KanbanStatus.RUNNING,
    KanbanStatus.BLOCKED,
    KanbanStatus.DONE,
    KanbanStatus.ARCHIVED,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "How this board works",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "The server owns the active board. Switching boards here " +
                    "switches every Hermes client.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpHeading("Statuses")
            HelpStatuses.forEach { status ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = KanbanStatus.label(status),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(88.dp),
                    )
                    Text(
                        text = KanbanStatus.description(status).orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpHeading("Normal flow")
            Text(
                text = "triage → todo → scheduled → ready → running → blocked → done. " +
                    "Archived sits apart from the flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpHeading("Rules")
            HelpRule("Only the dispatcher starts tasks. You can't set Running.")
            HelpRule("Unassigned tasks never run.")
            HelpRule("Scheduled is a pause, not a date. Resume it when you're ready.")
            HelpRule("Parent tasks gate Ready. Unfinished parents keep a task in Todo.")
            HelpRule("Done needs a result. The Complete sheet asks for one.")
            HelpRule("Archiving a running task stops its worker. Blocking does not.")
            HelpRule("Closing this app does not stop a running worker, and this version has no stop action.")
            HelpRule("There is no delete. Archive is the reversible way to remove work from the board.")
            HelpRule("Drag-and-drop isn't available on the phone; use Move to… on a task.")

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpHeading("Not in this version")
            Text(
                text = "Review actions, attachments and bulk actions are not part of " +
                    "this Kanban client yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HelpHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun HelpRule(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
