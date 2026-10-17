package com.dipandhali.hermesnative.domain.voice

import kotlin.math.sqrt

/**
 * Which flavor of voice input the composer is set to.
 *
 * Persisted by [prefValue] rather than by enum name, so renaming an entry in
 * code can never strand a stored preference.
 */
enum class VoiceInputMode(val prefValue: String) {
    /** Speech is transcribed server-side and sent as text. */
    TRANSCRIBE("transcribe"),

    /** The recording itself is sent as an audio attachment. */
    VOICE_NOTE("voice_note");

    companion object {
        /** Unknown or missing values fall back to [TRANSCRIBE]. */
        fun fromPref(raw: String?): VoiceInputMode =
            entries.firstOrNull { it.prefValue == raw } ?: TRANSCRIBE
    }
}

/**
 * The composer's voice state machine.
 *
 * Transient recording values (elapsed time, amplitude) are deliberately not
 * part of it: they tick at 10 Hz and belong to the recording strip's own
 * composition, not to ViewModel state the whole transcript observes.
 */
sealed interface VoicePhase {
    data object Idle : VoicePhase
    data object Recording : VoicePhase

    /**
     * Between stop and the automatic send; [label] is the pinned strip copy
     * (`Transcribing…` / `Sending…`).
     */
    data class Finishing(val label: String) : VoicePhase
}

/** The file-name prefix every recorded clip gets; also the display mapping key. */
const val VOICE_FILE_PREFIX = "voice-"

/** What a clip shows as in a transcript's attachment line. */
const val VOICE_MESSAGE_LABEL = "Voice message"

/** True for recorded clips ([VOICE_FILE_PREFIX]-named files). */
fun isVoiceClipName(name: String): Boolean = name.startsWith(VOICE_FILE_PREFIX)

/**
 * `m:ss`, or `h:mm:ss` once an hour is reached. Negative input clamps to
 * `0:00`. Formatted by hand rather than via `String.format`, which follows the
 * device locale and can emit non-ASCII digits in a timer.
 */
fun formatElapsed(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
}

/**
 * `MediaRecorder.maxAmplitude` (0..32767) → a 0..1 bar height for the strip.
 *
 * Square root on purpose: speech amplitude is perceptually closer to
 * logarithmic, and a linear bar spends most of its life looking like a flat
 * line. Values above the reference maximum clamp to 1.
 */
fun normalizeAmplitude(raw: Int): Float {
    if (raw <= 0) return 0f
    val fraction = (raw / MAX_AMPLITUDE.toDouble()).coerceIn(0.0, 1.0)
    return sqrt(fraction).toFloat().coerceIn(0f, 1f)
}

private const val MAX_AMPLITUDE = 32_767

/**
 * The message a dictation stop sends: the typed prefix preserved, the
 * transcript appended with a single space — the same resolution the web
 * client's `_commitTranscript` reaches for the server-STT path. Either side may
 * be empty.
 */
fun assembleDictation(typed: String, transcript: String): String {
    val base = typed.trim()
    val spoken = transcript.trim()
    return when {
        base.isEmpty() -> spoken
        spoken.isEmpty() -> base
        else -> "$base $spoken"
    }
}

/** Transcript display for a message's attachment names; clips read as [VOICE_MESSAGE_LABEL]. */
fun displayAttachmentNames(names: List<String>): String = names.joinToString(", ") { name ->
    if (isVoiceClipName(name)) VOICE_MESSAGE_LABEL else name
}
