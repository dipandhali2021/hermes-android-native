package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.ui.kanban.KanbanLiveState
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The board's honesty badge. Every state has words, not just a coloured dot:
 * "Offline" must be readable without relying on hue.
 */
@Composable
fun KanbanLiveBadge(
    live: KanbanLiveState,
    modifier: Modifier = Modifier,
) {
    if (live == KanbanLiveState.OFF) return

    val label = when (live) {
        KanbanLiveState.OFF -> ""
        KanbanLiveState.CONNECTING -> "Connecting"
        KanbanLiveState.LIVE -> "Live"
        KanbanLiveState.POLLING -> "Polling"
        KanbanLiveState.OFFLINE -> "Offline"
    }
    val target = when (live) {
        KanbanLiveState.OFF -> MaterialTheme.colorScheme.outline
        KanbanLiveState.CONNECTING -> MaterialTheme.colorScheme.tertiary
        KanbanLiveState.LIVE -> MaterialTheme.colorScheme.primary
        KanbanLiveState.POLLING -> MaterialTheme.colorScheme.tertiary
        KanbanLiveState.OFFLINE -> MaterialTheme.colorScheme.error
    }
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = HermesMotion.effects(),
        label = "kanban-live-dot",
    )

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Live updates: $label"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
