package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.composer.ContextMeter
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The composer's context-window control: a ring gauge with the used share
 * drawn and the remainder left as the quiet track — the web client's own
 * trigger shape, without the cramped centre digits. Tapping it opens the
 * context card as a small Material 3 bottom sheet.
 *
 * The ring carries no static glyph on purpose: the gauge itself answers "how
 * full is the context". Colour follows the web's bands: quiet under half,
 * warning over half, error past three quarters.
 *
 * The card shows what the server actually reports — used/window, headroom,
 * the auto-compress point, and the session's cumulative counters. The server
 * exposes no per-category composition (system prompt / tools / files), so the
 * card does not invent one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextIndicator(
    meter: ContextMeter,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    IconButton(
        onClick = {
            // The card owns the bottom of the screen; drop the keyboard first
            // so it opens into free space — the attach panel's contract.
            focusManager.clearFocus()
            sheetOpen = true
        },
        modifier = modifier.semantics { contentDescription = meter.contentDescription },
    ) {
        ContextRing(meter)
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            ContextSheet(meter)
        }
    }
}

@Composable
private fun ContextRing(meter: ContextMeter) {
    val sweep by animateFloatAsState(
        targetValue = (meter.pct ?: 0) / 100f,
        animationSpec = HermesMotion.spatialFast(),
        label = "context-ring-sweep",
    )
    val arcColor by animateColorAsState(
        targetValue = when {
            meter.pct == null -> MaterialTheme.colorScheme.onSurfaceVariant
            meter.pct > 75 -> MaterialTheme.colorScheme.error
            meter.pct > 50 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = HermesMotion.effects(),
        label = "context-ring-arc",
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(Modifier.size(24.dp)) {
        // Proportions of the web's own 24-unit viewBox: r=9.75, stroke 3.
        val stroke = size.width * (3.25f / 24f)
        val inset = size.width * (2.5f / 24f)
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        val strokeStyle = Stroke(width = stroke, cap = StrokeCap.Round)

        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = strokeStyle,
        )
        if (meter.pct != null && sweep > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = strokeStyle,
            )
        }
    }
}

@Composable
private fun ContextSheet(meter: ContextMeter) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Context",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = meter.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.tertiary,
            )
        }

        if (meter.hasWindowPair) {
            Spacer(Modifier.height(14.dp))
            ContextBar(
                meter = meter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Spacer(Modifier.height(12.dp))
            ContextRow(
                dot = scheme.primary,
                label = meter.usedRowLabel,
                value = ContextMeter.formatGrouped(meter.contextTokens),
            )
            meter.headroomTokens?.let { headroom ->
                ContextRow(
                    dot = scheme.outline,
                    label = "Headroom",
                    value = ContextMeter.formatGrouped(headroom),
                )
            }
            meter.thresholdValue?.let { value ->
                ContextRow(
                    dot = scheme.onSurfaceVariant,
                    label = "Auto-compress",
                    value = value,
                )
            }
            meter.exceededCaption?.let { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No context window reported for this session.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        SessionTotals(meter)
    }
}

/** The cumulative session counters, separated from the live window figures. */
@Composable
private fun SessionTotals(meter: ContextMeter) {
    val rows = buildList {
        if (meter.inTokens > 0) add("Input" to ContextMeter.formatGrouped(meter.inTokens))
        if (meter.outTokens > 0) add("Output" to ContextMeter.formatGrouped(meter.outTokens))
        if (meter.cacheReadTokens > 0) add("Cache read" to ContextMeter.formatGrouped(meter.cacheReadTokens))
        if (meter.cacheWriteTokens > 0) add("Cache write" to ContextMeter.formatGrouped(meter.cacheWriteTokens))
        meter.costLabel?.let { add("Cost" to it) }
    }
    if (rows.isEmpty()) return
    val scheme = MaterialTheme.colorScheme

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 14.dp),
        color = scheme.outlineVariant,
    )
    Text(
        text = "Session totals",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.onSurfaceVariant,
    )
    rows.forEach { (label, value) ->
        ContextRow(dot = scheme.outline, label = label, value = value)
    }
}

/**
 * The stacked bar: used share in the brand colour over a quiet track, with a
 * thin mark at the auto-compress point when the session knows it.
 */
@Composable
private fun ContextBar(meter: ContextMeter, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val used = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        val usedWidth = size.width * meter.usedFraction
        if (usedWidth > 0f) {
            drawRoundRect(
                color = used,
                size = Size(usedWidth, size.height),
                cornerRadius = radius,
            )
        }
        meter.thresholdFraction?.let { fraction ->
            val x = size.width * fraction
            drawLine(
                color = marker,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ContextRow(dot: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
