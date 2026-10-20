package com.dipandhali.hermesnative.domain.kanban

import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class KanbanDispatchRow(
    val key: String,
    val label: String,
    val count: Int? = null,
    val text: String? = null,
    val details: List<String> = emptyList(),
    val warning: Boolean = false,
)

data class KanbanDispatchSummary(
    val dryRun: Boolean,
    val locked: Boolean = false,
    val memoryPressure: String? = null,
    val rows: List<KanbanDispatchRow> = emptyList(),
    val nothingToDo: Boolean = false,
)

/**
 * Renders whatever the dispatcher returned. The bridge serialises
 * `DispatchResult` as a dict, and a newer agent can add a bucket at any time;
 * no fixed field list is allowed to hide it.
 */
object KanbanDispatch {
    private val labels = mapOf(
        "spawned" to "Spawned",
        "promoted" to "Promoted",
        "reclaimed" to "Reclaimed",
        "skipped_unassigned" to "Skipped — unassigned",
        "skipped_nonspawnable" to "Skipped — not spawnable",
        "skipped_per_profile_capped" to "Skipped — profile at capacity",
        "auto_assigned_default" to "Auto-assigned",
        "auto_blocked" to "Auto-blocked",
        "timed_out" to "Timed out",
        "crashed" to "Crashed",
        "stale" to "Stale",
        "respawn_guarded" to "Respawn-guarded",
        "rate_limited" to "Rate-limited",
        "reconciled_orphans" to "Reconciled orphans",
        "reaped_terminal_workers" to "Reaped terminal workers",
    )

    private val warningKeys = setOf(
        "skipped_unassigned",
        "auto_blocked",
        "timed_out",
        "crashed",
        "stale",
        "respawn_guarded",
        "rate_limited",
    )

    fun summary(result: JsonObject, dryRun: Boolean): KanbanDispatchSummary {
        var locked = false
        var memoryPressure: String? = null
        val rows = mutableListOf<KanbanDispatchRow>()

        for ((key, value) in result) {
            when (key) {
                "skipped_locked" -> {
                    locked = (value as? JsonPrimitive)?.booleanOrNull == true
                    continue
                }

                "memory_pressure" -> {
                    memoryPressure = (value as? JsonPrimitive)
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    continue
                }

                "dry_run" -> continue
            }

            val row = row(key, value) ?: continue
            rows += row
        }

        return KanbanDispatchSummary(
            dryRun = dryRun,
            locked = locked,
            memoryPressure = memoryPressure,
            rows = rows,
            nothingToDo = rows.isEmpty() && !locked && memoryPressure == null,
        )
    }

    private fun row(key: String, value: JsonElement): KanbanDispatchRow? {
        if (value is JsonNull) return null
        val label = labels[key] ?: humanize(key)
        val warning = key in warningKeys
        return when (value) {
            is JsonArray -> {
                if (value.isEmpty()) return null
                val details = value.mapNotNull { detailOf(it) }
                if (details.isEmpty()) return null
                KanbanDispatchRow(
                    key = key,
                    label = label,
                    count = value.size,
                    text = firstDetail(value),
                    details = details,
                    warning = warning,
                )
            }

            is JsonPrimitive -> primitiveRow(key, label, value, warning)
            is JsonObject -> if (value.isEmpty()) {
                null
            } else {
                KanbanDispatchRow(
                    key = key,
                    label = label,
                    text = value.toString(),
                    warning = warning,
                )
            }

            else -> null
        }
    }

    private fun primitiveRow(
        key: String,
        label: String,
        value: JsonPrimitive,
        warning: Boolean,
    ): KanbanDispatchRow? {
        if (value.isString) {
            val text = value.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            return KanbanDispatchRow(key, label, text = text, warning = warning)
        }

        val boolean = value.booleanOrNull
        if (boolean != null) {
            return if (boolean) {
                KanbanDispatchRow(key, label, text = "true", warning = warning)
            } else {
                null
            }
        }

        val number = value.intOrNull
        if (number != null) {
            return if (number == 0) {
                null
            } else {
                KanbanDispatchRow(key, label, count = number, warning = warning)
            }
        }

        val text = value.contentOrNull
            ?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?: return null
        return KanbanDispatchRow(key, label, text = text, warning = warning)
    }

    private fun detailOf(element: JsonElement): String? = when (element) {
        is JsonArray -> element
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            .joinToString(" — ")
            .takeIf { it.isNotBlank() }

        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> element.toString().takeIf { it != "{}" }
        else -> null
    }

    private fun firstDetail(array: JsonArray): String? = when (val first = array.firstOrNull()) {
        is JsonArray -> (first.firstOrNull() as? JsonPrimitive)?.contentOrNull
        is JsonPrimitive -> first.contentOrNull
        else -> null
    }

    private fun humanize(raw: String): String {
        val words = raw.split('_', '-', ' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return raw
        return words
            .joinToString(" ") { it.lowercase(Locale.ENGLISH) }
            .replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }
}
