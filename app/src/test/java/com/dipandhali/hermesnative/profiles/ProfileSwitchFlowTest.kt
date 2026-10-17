package com.dipandhali.hermesnative.profiles

import com.dipandhali.hermesnative.domain.composer.ProfileSwitchMode
import com.dipandhali.hermesnative.domain.composer.profileSwitchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSwitchFlowTest {

    @Test
    fun `an empty idle conversation re-tags in place`() {
        assertEquals(ProfileSwitchMode.ReTagInPlace, profileSwitchMode(hasTranscript = false, runInFlight = false))
    }

    @Test
    fun `a conversation with history becomes a replacement chat`() {
        assertEquals(ProfileSwitchMode.ReplaceChat, profileSwitchMode(hasTranscript = true, runInFlight = false))
    }

    @Test
    fun `a run in flight also becomes a replacement chat`() {
        assertEquals(
            ProfileSwitchMode.ReplaceChat,
            profileSwitchMode(hasTranscript = false, runInFlight = true),
        )
    }
}
