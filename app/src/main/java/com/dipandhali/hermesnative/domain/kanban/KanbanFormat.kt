package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A staleness pill on a task card. Null means "nothing to call out". */
data class Staleness(val level: Level, val label: String) {
    enum class Level { INFO, WARNING, ERROR }
}

object KanbanFormat {
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    /**
     * A compact age. Under a minute is "just now"; minutes, hours and days are
     * truncated; older than a week becomes the calendar date of `now - age`.
     */
    fun compactAge(seconds: Double?): String {
        if (seconds == null) return "—"
        val s = seconds.coerceAtLeast(0.0)
        return when {
            s < 60 -> "just now"
            s < 3_600 -> "${(s / 60).toInt()}m"
            s < 86_400 -> "${(s / 3_600).toInt()}h"
            s < 604_800 -> "${(s / 86_400).toInt()}d"
            else -> Instant.now()
                .minusSeconds(s.toLong())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(dateFormat)
        }
    }

    /**
     * The card footer's age. The bridge provides three ages; the status decides
     * which one is meaningful. Done shows how long it took, running how long the
     * worker has held it, everything else how long it has existed.
     */
    fun footerAge(task: KanbanTaskDto): String = when (task.status) {
        KanbanStatus.RUNNING -> compactAge(task.effectiveAge?.startedAgeSeconds)
        KanbanStatus.DONE -> compactAge(task.effectiveAge?.timeToCompleteSeconds)
        else -> compactAge(task.effectiveAge?.createdAgeSeconds)
    }

    fun duration(start: Double?, end: Double?): String {
        if (start == null || end == null) return "—"
        val s = (end - start).coerceAtLeast(0.0)
        return when {
            s < 60 -> "${s.toInt()}s"
            s < 3_600 -> "${(s / 60).toInt()}m"
            s < 86_400 -> "${(s / 3_600).toInt()}h"
            else -> "${(s / 86_400).toInt()}d"
        }
    }

    /**
     * Markdown stripped for a two-line card preview or search. This is not a
     * renderer: it removes the syntax that would look like noise in plain text,
     * keeps the words, and caps the result.
     */
    fun plainPreview(markdown: String?, maxChars: Int = 160): String {
        if (markdown.isNullOrBlank()) return ""
        var text = markdown
        text = text.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        text = text.replace(Regex("(?m)^\\s*```.*$"), "")
        text = text.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("(\\*\\*|__|~~|\\*|_)"), "")
        text = text.replace("`", "")
        text = text.replace(Regex("\\s+"), " ").trim()
        if (text.length <= maxChars) return text
        if (maxChars <= 0) return ""
        return text.take(maxChars - 1).trimEnd() + "…"
    }

    /** "review" -> "Review"; unknown names are shown, never dropped. */
    fun prettyStatus(raw: String): String = raw
        .split('_', '-', ' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        }
        .ifEmpty { raw }
}

object KanbanStaleness {
    fun of(task: KanbanTaskDto): Staleness? {
        val age = task.effectiveAge ?: return null
        return when (task.status) {
            KanbanStatus.RUNNING -> {
                val started = age.startedAgeSeconds ?: return null
                when {
                    started > 3_600 -> Staleness(Staleness.Level.ERROR, "stale")
                    started > 600 -> Staleness(Staleness.Level.WARNING, "slow")
                    else -> null
                }
            }

            KanbanStatus.BLOCKED -> {
                val created = age.createdAgeSeconds ?: return null
                when {
                    created > 86_400 -> Staleness(Staleness.Level.ERROR, "stale")
                    created > 3_600 -> Staleness(
                        Staleness.Level.WARNING,
                        "blocked ${KanbanFormat.compactAge(created)}",
                    )

                    else -> null
                }
            }

            KanbanStatus.READY -> {
                val created = age.createdAgeSeconds ?: return null
                if (created > 3_600) {
                    Staleness(
                        Staleness.Level.WARNING,
                        "waiting ${KanbanFormat.compactAge(created)}",
                    )
                } else {
                    null
                }
            }

            else -> null
        }
    }
}
