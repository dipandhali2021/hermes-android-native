package com.dipandhali.hermesnative.data.auth

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AgentCapabilities
import com.dipandhali.hermesnative.core.model.AgentHealth

/**
 * Decides whether a URL actually belongs to a Hermes agent API server.
 *
 * Fail closed: an unknown platform, a missing capabilities block, or a non-bearer
 * auth mode is not an agent. `/health` alone is not evidence — any Hermes WebUI
 * answers it too, and the two surfaces need different credentials and routes.
 */
object AgentConnectionValidator {

    fun validate(health: AgentHealth, capabilities: AgentCapabilities): DataError? {
        if (health.platform != AGENT_PLATFORM) return DataError.NotAgent
        if (capabilities.platform != AGENT_PLATFORM) return DataError.NotAgent
        if (capabilities.auth?.type != "bearer") return DataError.NotAgent
        return null
    }

    private const val AGENT_PLATFORM = "hermes-agent"
}
