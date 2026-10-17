package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipandhali.hermesnative.domain.composer.ContextMeter
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The composer's context-window control: a progress ring with the percent
 * inside — the web client's own trigger (`static/style.css` `.ctx-ring`) —
 * between the mic and send. Tapping it opens the same card the web tooltip
 * shows.
 *
 * The ring is the whole icon on purpose: no static glyph says "how full is
 * the context", and the web learned this too — its indicator is the gauge,
 * not a symbol. Colour follows the web's bands: quiet under half, warning
 * over half, error past three quarters.
 *
 * The card is a Material 3 [DropdownMenu]: it anchors to the button, flips
 * above the composer when the keyboard owns the space below, and dismisses
 * on outside tap or back — the platform's equivalent of the web popover
 * (which is a plain absolutely-positioned tooltip).
 */
@Composable
fun ContextIndicator(
    meter: ContextMeter,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.semantics { contentDescription = meter.contentDescription },
        ) {
            ContextRing(meter)
        }
        ContextWindowMenu(
            meter = meter,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
        )
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

    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Proportions of the web's own 24-unit viewBox: r=9.75, stroke 3.
            val stroke = size.width * (3f / 24f)
            val pad = size.width * (2.25f / 24f)
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            val topLeft = Offset(pad, pad)
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
        // "·" when there is no prompt/window pair to report, exactly like the
        // web ring's centre.
        Text(
            text = meter.pct?.toString() ?: "·",
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContextWindowMenu(
    meter: ContextMeter,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 186.dp, max = 226.dp)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Context window",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Text(
                text = meter.usageLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = meter.tokensLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            meter.thresholdLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
