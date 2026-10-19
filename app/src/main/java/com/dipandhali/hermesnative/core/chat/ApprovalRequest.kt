package com.dipandhali.hermesnative.core.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One destructive-command approval prompt.
 *
 * The agent and the WebUI use different field spellings for the same concept
 * (`approval_id`/`id`, `tool`/`function_name`, `description`/`prompt`); the
 * parser accepts both so one dialog can serve either backend.
 */
data class ApprovalRequest(
    val id: String? = null,
    val tool: String? = null,
    val command: String? = null,
    val description: String? = null,
    val riskLevel: String? = null,
    val choices: List<String> = DEFAULT_CHOICES,
    val allowPermanent: Boolean = false,
    val runId: String? = null,
    val args: kotlinx.serialization.json.JsonElement? = null,
) {
    val hasAnything: Boolean get() = !tool.isNullOrBlank() || !command.isNullOrBlank() || !description.isNullOrBlank()

    companion object {
        val DEFAULT_CHOICES = listOf("once", "session", "always", "deny")
    }
}

object ApprovalRequestParser {

    fun parse(payload: JsonObject?): ApprovalRequest {
        if (payload == null) return ApprovalRequest()

        fun str(vararg keys: String): String? {
            for (key in keys) {
                val value = (payload[key] as? JsonPrimitive)?.contentOrNull
                if (!value.isNullOrBlank()) return value
            }
            return null
        }

        val choices = (payload["choices"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: ApprovalRequest.DEFAULT_CHOICES

        val allowPermanent = (payload["allow_permanent"] as? JsonPrimitive)
            ?.contentOrNull
            ?.toBooleanStrictOrNull()
            ?: choices.contains("always")

        return ApprovalRequest(
            id = str("approval_id", "id"),
            tool = str("tool", "function_name", "name", "pattern_key"),
            command = str("command"),
            description = str("description", "prompt"),
            riskLevel = str("risk_level"),
            choices = choices,
            allowPermanent = allowPermanent,
            runId = str("run_id"),
            args = payload["args"],
        )
    }

    /** The label a person should see on the allow-once button. */
    fun labelFor(choice: String): String = when (choice) {
        "once" -> "Allow once"
        "session" -> "Allow this session"
        "always" -> "Always allow"
        "deny" -> "Deny"
        else -> choice.replaceFirstChar { it.uppercase() }
    }
}
