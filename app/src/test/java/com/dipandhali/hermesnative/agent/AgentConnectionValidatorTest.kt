package com.dipandhali.hermesnative.agent

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AgentAuth
import com.dipandhali.hermesnative.core.model.AgentCapabilities
import com.dipandhali.hermesnative.core.model.AgentHealth
import com.dipandhali.hermesnative.data.auth.AgentConnectionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The connect probe must fail closed: a server that answers `/health` but is
 * not an agent API server must never be accepted as one, and an agent with
 * non-bearer auth is not the deployment this mode speaks to.
 */
class AgentConnectionValidatorTest {

    @Test
    fun `a bearer agent is accepted`() {
        assertNull(
            AgentConnectionValidator.validate(
                health = AgentHealth(status = "ok", platform = "hermes-agent", version = "0.19.0"),
                capabilities = AgentCapabilities(
                    platform = "hermes-agent",
                    model = "hermes-agent",
                    auth = AgentAuth(type = "bearer", required = true),
                ),
            ),
        )
    }

    @Test
    fun `a webui health payload is rejected`() {
        assertEquals(
            DataError.NotAgent,
            AgentConnectionValidator.validate(
                health = AgentHealth(status = "ok", version = "1.0"),
                capabilities = AgentCapabilities(platform = "hermes-agent", auth = AgentAuth("bearer")),
            ),
        )
    }

    @Test
    fun `a non-agent capabilities payload is rejected`() {
        assertEquals(
            DataError.NotAgent,
            AgentConnectionValidator.validate(
                health = AgentHealth(status = "ok", platform = "hermes-agent"),
                capabilities = AgentCapabilities(platform = "something-else", auth = AgentAuth("bearer")),
            ),
        )
    }

    @Test
    fun `missing or non-bearer auth is rejected`() {
        assertEquals(
            DataError.NotAgent,
            AgentConnectionValidator.validate(
                health = AgentHealth(status = "ok", platform = "hermes-agent"),
                capabilities = AgentCapabilities(platform = "hermes-agent", auth = null),
            ),
        )
        assertEquals(
            DataError.NotAgent,
            AgentConnectionValidator.validate(
                health = AgentHealth(status = "ok", platform = "hermes-agent"),
                capabilities = AgentCapabilities(platform = "hermes-agent", auth = AgentAuth("cookie")),
            ),
        )
    }
}
