package com.dipandhali.hermesnative.data.voice

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.dipandhali.hermesnative.domain.voice.VOICE_FILE_PREFIX
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recorder shell: `MediaRecorder` behind a small, total API.
 *
 * Deliberately logic-free — the pitch is that nothing here is worth mocking,
 * so the behaviour that can be tested lives in `domain/voice/VoiceInput.kt`
 * instead. Every method degrades instead of crashing: amplitude and elapsed
 * time read as zero when idle or dead, and a failed stop destroys its own
 * partial clip rather than leaving a zero-byte file to upload.
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var recorder: MediaRecorder? = null
    private var clip: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /**
     * Starts an AAC/MPEG-4 recording into the cache and returns its
     * FileProvider URI, which both the uploader and the transcription post can
     * read like any other content URI. Old clips are pruned on the way in.
     */
    fun start(): Result<Uri> {
        if (recorder != null) {
            return Result.failure(IllegalStateException("Already recording."))
        }
        return runCatching {
            pruneOldClips()
            val dir = File(context.cacheDir, "voice").apply { mkdirs() }
            val out = File(dir, "$VOICE_FILE_PREFIX${System.currentTimeMillis()}.m4a")
            val media = newMediaRecorder()
            try {
                media.setAudioSource(MediaRecorder.AudioSource.MIC)
                media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                media.setAudioSamplingRate(44_100)
                media.setAudioEncodingBitRate(64_000)
                media.setOutputFile(out.absolutePath)
                media.prepare()
                media.start()
            } catch (t: Throwable) {
                runCatching { media.release() }
                out.delete()
                throw t
            }
            recorder = media
            clip = out
            startedAt = SystemClock.elapsedRealtime()
            uriOf(out)
        }
    }

    /**
     * Stops the recording and returns the clip's URI.
     *
     * `MediaRecorder.stop()` throws when no frame was captured — a stop within
     * the first moments of a tap-to-talk — so that case maps to the pinned
     * too-short message and the partial clip is deleted; the caller shows the
     * message and sends nothing.
     */
    fun stop(): Result<Uri> {
        val media = recorder ?: return Result.failure(IllegalStateException("Not recording."))
        val out = clip
        val result = runCatching {
            try {
                media.stop()
            } catch (t: RuntimeException) {
                throw IllegalStateException(TOO_SHORT_MESSAGE, t)
            } finally {
                runCatching { media.release() }
                recorder = null
                clip = null
                startedAt = 0L
            }
            val file = out ?: throw IllegalStateException(TOO_SHORT_MESSAGE)
            uriOf(file)
        }
        return result.onFailure { out?.delete() }
    }

    /** Discards an in-flight recording (and its partial file). Total: safe in any state. */
    fun cancel() {
        val media = recorder
        val out = clip
        recorder = null
        clip = null
        startedAt = 0L
        if (media != null) {
            runCatching { media.stop() }
            runCatching { media.release() }
        }
        out?.delete()
    }

    /** Peak amplitude since the last read, 0..32767; 0 when idle or dead. */
    fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /** Milliseconds since the recording started; 0 when idle. */
    fun elapsedMs(): Long =
        if (recorder == null) 0L else SystemClock.elapsedRealtime() - startedAt

    /** Process-teardown alias for [cancel] — frees the microphone, drops the clip. */
    fun release() = cancel()

    private fun uriOf(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Clips are one-shot; anything a day old is an orphan from a lost run. */
    private fun pruneOldClips() {
        val dir = File(context.cacheDir, "voice")
        val cutoff = System.currentTimeMillis() - CLIP_TTL_MS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    private companion object {
        const val TOO_SHORT_MESSAGE = "Recording was too short — try again."
        const val CLIP_TTL_MS = 24L * 60 * 60 * 1000
    }
}
