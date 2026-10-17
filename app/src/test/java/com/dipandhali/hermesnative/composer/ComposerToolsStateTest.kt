package com.dipandhali.hermesnative.composer

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.domain.composer.rowHiddenByMissingApi
import com.dipandhali.hermesnative.domain.composer.rowHiddenByMissingEndpoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The availability rule for a chat-tools row: a server older than the
 * endpoint answers 404 and the row hides quietly; every other failure keeps
 * the row and surfaces the error when it is tapped.
 */
class ComposerToolsStateTest {

    @Test
    fun `a bare 404 hides the row`() {
        assertTrue(rowHiddenByMissingEndpoint(DataError.NotFound))
        assertTrue(rowHiddenByMissingApi(ApiException(DataError.NotFound)))
    }

    @Test
    fun `a 404 carrying a message also hides the row`() {
        val error = DataError.Server(404, "Not found")
        assertTrue(rowHiddenByMissingEndpoint(error))
    }

    @Test
    fun `other statuses and failures keep the row`() {
        assertFalse(rowHiddenByMissingEndpoint(null))
        assertFalse(rowHiddenByMissingEndpoint(DataError.Server(400, "text too long")))
        assertFalse(rowHiddenByMissingEndpoint(DataError.Server(403, "Profile is bound")))
        assertFalse(rowHiddenByMissingEndpoint(DataError.Unreachable))
        assertFalse(rowHiddenByMissingEndpoint(DataError.Malformed("no")))
    }
}
