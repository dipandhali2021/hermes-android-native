package com.dipandhali.hermesnative.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import kotlinx.coroutines.delay

/** One action in a [FabMenu]. */
data class FabMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val selected: Boolean = false,
)

/**
 * A Material 3 Expressive FAB menu.
 *
 * Hand-built because `FloatingActionButtonMenu` is not public until material3
 * 1.5.x; the shape and motion here follow the spec: pill-shaped items carrying an
 * icon *and* a label, stacked above the button, with the button morphing between
 * "add" and "close" rather than swapping in place.
 *
 * Two deliberate departures from the reference, both for a phone:
 *
 * - Items carry text labels rather than relying on icon recognition. The desktop
 *   rail is a row of bare icons because a mouse can hover for a tooltip; a thumb
 *   cannot, and nine unlabelled glyphs are nine guesses.
 * - The menu is bottom-end aligned inside a scrim-free area rather than centred,
 *   so it sits in the thumb's arc above the composer instead of under the
 *   hand holding the phone.
 *
 * Accessibility: the items are real buttons with labels, and the collapsed FAB
 * advertises every action as a custom accessibility action, so a TalkBack user
 * does not have to discover and open the menu to learn what is in it.
 */
@Composable
fun FabMenu(
    actions: List<FabMenuAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    maxItemsHeight: androidx.compose.ui.unit.Dp = 360.dp,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        animationSpec = HermesMotion.spatial(),
        label = "fab-menu-rotation",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Items animate outwards from the button: the last one is furthest away, so
        // it enters first and leaves last, which reads as the stack unfolding
        // rather than being painted on.
        //
        // The stack is height-capped and scrolls because nine destinations are
        // taller than a phone screen: without the cap the first items would be
        // pushed off the top where they cannot be reached at all.
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .heightIn(max = maxItemsHeight)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.reversed().forEachIndexed { reversedIndex, action ->
                val index = actions.lastIndex - reversedIndex
                FabMenuItem(
                    action = action,
                    visible = expanded,
                    delayMillis = if (expanded) (actions.lastIndex - index) * 24L else index * 18L,
                )
            }
        }

        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            shape = CircleShape,
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 3.dp,
            ),
            modifier = Modifier
                .size(56.dp)
                .semantics {
                    customActions = actions.map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.onClick()
                            true
                        }
                    }
                },
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Add,
                contentDescription = if (expanded) "Close menu" else "More actions",
                modifier = Modifier
                    .size(26.dp)
                    .rotate(rotation),
            )
        }
    }
}

/**
 * One pill item.
 *
 * `delayMillis` gives the staggered entry the spec calls for; the visibility
 * animation itself is a scale from the bottom end so items appear to grow out of
 * the button.
 */
@Composable
private fun FabMenuItem(
    action: FabMenuAction,
    visible: Boolean,
    delayMillis: Long,
) {
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMillis)
            shown = true
        } else {
            shown = false
        }
    }

    AnimatedVisibility(
        visible = shown,
        enter = scaleIn(
            animationSpec = HermesMotion.spatialFast(),
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f),
        ) + fadeIn(),
        exit = scaleOut(
            animationSpec = tween(120),
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f),
        ) + fadeOut(),
    ) {
        val container = if (action.selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val content = if (action.selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

        Surface(
            color = container,
            contentColor = content,
            shape = CircleShape,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .widthIn(min = 112.dp)
                .heightIn(min = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = action.onClick,
                ),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A transparent catcher that closes the menu when the user taps outside it.
 *
 * Sits behind the menu in the layout, so it never steals the menu's own taps.
 */
@Composable
fun FabMenuScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .then(Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}
