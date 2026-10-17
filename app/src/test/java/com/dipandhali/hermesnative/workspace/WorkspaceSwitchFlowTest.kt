package com.dipandhali.hermesnative.workspace

import com.dipandhali.hermesnative.domain.composer.WORKSPACE_SWITCH_BLOCKED_COPY
import com.dipandhali.hermesnative.domain.composer.WorkspaceSwitchCheck
import com.dipandhali.hermesnative.domain.composer.homeSendWorkspace
import com.dipandhali.hermesnative.domain.composer.workspaceSwitchAllowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceSwitchFlowTest {

    @Test
    fun `an idle chat allows the switch`() {
        assertEquals(
            WorkspaceSwitchCheck.Allowed,
            workspaceSwitchAllowed(isBusy = false, isStreaming = false),
        )
    }

    @Test
    fun `busy or streaming refuses with the fixed copy`() {
        assertEquals(
            WorkspaceSwitchCheck.Blocked,
            workspaceSwitchAllowed(isBusy = true, isStreaming = false),
        )
        assertEquals(
            WorkspaceSwitchCheck.Blocked,
            workspaceSwitchAllowed(isBusy = false, isStreaming = true),
        )
    }

    @Test
    fun `the blocked copy is the promised sentence`() {
        assertEquals("Finish or stop the reply before switching workspace.", WORKSPACE_SWITCH_BLOCKED_COPY)
    }

    @Test
    fun `home target prefers the pending pick over the identity default`() {
        assertEquals("/home/dipandhali/work", homeSendWorkspace("/home/dipandhali/work", "/home/user"))
        assertEquals("/home/user", homeSendWorkspace(null, "/home/user"))
        assertNull(homeSendWorkspace(null, null))
    }
}
