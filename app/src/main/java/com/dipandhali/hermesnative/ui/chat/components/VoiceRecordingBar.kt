package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.voice.VoicePhase
import com.dipandhali.hermesnative.domain.voice.formatElapsed
import com.dipandhali.hermesnative.domain.voice.normalizeAmplitude
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import kotlinx.coroutines.delay

/**
 * The composer's recording strip: cancel, elapsed time, a live waveform, and
 * the send-recording button — or, while a clip is being turned into a message,
 * a spinner and the pinned status label.
 *
 * The waveform polls the recorder's peak amplitude every [POLL_MS] into
 * *local* composition state. That is deliberate: the values change at 10 Hz,
 * and routing them through the ViewModel would republish the whole chat state
 * and recompose the transcript ten times a second for a decoration.
 */
@Composable
fun VoiceRecordingBar(
    phase: VoicePhase,
    elapsedMsProvider: () -> Long,
    amplitudeProvider: () -> Int,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (phase) {
        VoicePhase.Idle -> Unit
        VoicePhase.Recording -> RecordingRow(
            elapsedMsProvider = elapsedMsProvider,
            amplitudeProvider = amplitudeProvider,
            onCancel = onCancel,
            onStop = onStop,
            modifier = modifier,
        )

        is VoicePhase.Finishing -> FinishingRow(label = phase.label, modifier = modifier)
    }
}

@Composable
private fun RecordingRow(
    elapsedMsProvider: () -> Long,
    amplitudeProvider: () -> Int,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var levels by remember { mutableStateOf(emptyList<Float>()) }

    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = elapsedMsProvider()
            levels = (levels + normalizeAmplitude(amplitudeProvider())).takeLast(LEVEL_COUNT)
            delay(POLL_MS)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cancel recording",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = formatElapsed(elapsedMs),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        // The waveform takes the space the text field would have; bars enter
        // left-aligned and the newest sample is always the rightmost.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(BAR_MAX)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            levels.forEach { level -> WaveformBar(level = level) }
        }

        // Same error-role fill as the assistant Stop button: a tap here ends
        // the capture and commits it, not a neutral action.
        FilledIconButton(
            onClick = onStop,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = "Send recording", Modifier.size(20.dp))
        }
    }
}

@Composable
private fun WaveformBar(level: Float) {
    val barHeight by animateDpAsState(
        targetValue = BAR_MIN + (BAR_MAX - BAR_MIN) * level,
        animationSpec = HermesMotion.spatialFast(),
        label = "waveform-bar",
    )
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(barHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun FinishingRow(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private const val POLL_MS = 100L
private const val LEVEL_COUNT = 24
private val BAR_MIN = 4.dp
private val BAR_MAX = 28.dp
