package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The model's thinking, as a collapsible disclosure.
 *
 * Behaviour borrowed from assistant-ui's reasoning element and kept because it is
 * right: the card **opens while reasoning is arriving and closes itself on the
 * first answer token**. The user wants the answer; thinking stays available on
 * demand without ever pushing the reply off screen. Auto-collapsing only fires
 * once per turn, so a user who reopens it is not fought by the animation.
 */
@Composable
fun ReasoningCard(
    reasoning: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    hasAnswerStarted: Boolean = false,
    defaultExpanded: Boolean = false,
) {
    if (reasoning.isBlank()) return

    var expanded by remember { mutableStateOf(defaultExpanded || isActive) }
    var userToggled by remember { mutableStateOf(false) }

    // Auto-collapse when the answer starts, but never overrule a deliberate tap.
    LaunchedEffect(hasAnswerStarted) {
        if (hasAnswerStarted && !userToggled) expanded = false
    }
    LaunchedEffect(isActive) {
        if (isActive && !userToggled) expanded = true
    }

    val label = when {
        isActive -> "Thinking…"
        else -> "Thought process"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(HermesMotion.contentSize),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        userToggled = true
                        expanded = !expanded
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThinkingIndicator(active = isActive)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = HermesMotion.spatialFast(),
                    label = "reasoning-chevron",
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Hide thinking" else "Show thinking",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(HermesMotion.spatial()) + fadeIn(),
                exit = shrinkVertically(HermesMotion.spatial()) + fadeOut(),
            ) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                )
            }
        }
    }
}

/** Three dots that fade in sequence while the model has produced nothing yet. */
@Composable
fun ThinkingIndicator(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val dot by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking-dot",
    )

    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .alpha(if (active) dot else 0.35f)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
