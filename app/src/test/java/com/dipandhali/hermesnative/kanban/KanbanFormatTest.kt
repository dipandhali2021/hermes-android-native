package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.model.KanbanAgeDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.domain.kanban.KanbanStaleness
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.Staleness
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KanbanFormatTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `compact age`() {
        assertEquals("—", KanbanFormat.compactAge(null))
        assertEquals("just now", KanbanFormat.compactAge(0.0))
        assertEquals("just now", KanbanFormat.compactAge(59.0))
        assertEquals("1m", KanbanFormat.compactAge(60.0))
        assertEquals("2h", KanbanFormat.compactAge(7200.0))
        assertEquals("3d", KanbanFormat.compactAge(259200.0))

        val age = 8.0 * 86400.0
        val expected = LocalDate.now(ZoneId.systemDefault())
            .minusDays(8)
            .format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        assertEquals(expected, KanbanFormat.compactAge(age))
    }

    @Test
    fun `footer age by status`() {
        assertEquals(
            "2h",
            KanbanFormat.footerAge(task(KanbanStatus.READY, created = 7200.0)),
        )
        assertEquals(
            "2m",
            KanbanFormat.footerAge(task(KanbanStatus.RUNNING, created = 99999.0, started = 120.0)),
        )
        assertEquals(
            "1h",
            KanbanFormat.footerAge(task(KanbanStatus.DONE, completed = 3600.0)),
        )
        assertEquals(
            "—",
            KanbanFormat.footerAge(task(KanbanStatus.READY, created = null)),
        )
    }

    @Test
    fun `duration returns a dash for a missing endpoint`() {
        assertEquals("—", KanbanFormat.duration(null, 10.0))
        assertEquals("—", KanbanFormat.duration(10.0, null))
        assertEquals("1m", KanbanFormat.duration(0.0, 60.0))
        assertEquals("1h", KanbanFormat.duration(0.0, 3600.0))
    }

    @Test
    fun `plain preview strips markdown`() {
        val markdown = """
            # Title

            This is **bold** and `code` with [a link](https://example.com).

            ```
            val x = 1
            ```
        """.trimIndent()

        val preview = KanbanFormat.plainPreview(markdown)

        assertFalse(preview.contains("#"))
        assertFalse(preview.contains("**"))
        assertFalse(preview.contains("`"))
        assertFalse(preview.contains("](https"))
        assertTrue(preview.contains("Title"))
        assertTrue(preview.contains("bold"))
        assertTrue(preview.contains("a link"))
        assertTrue(preview.contains("val x = 1"))
        assertTrue(preview.length <= 160)

        val capped = KanbanFormat.plainPreview("abcdefghij", maxChars = 5)
        assertTrue(capped.length <= 5)
        assertTrue(capped.endsWith("…"))
    }

    @Test
    fun `pretty status title-cases unknown names`() {
        assertEquals("Review", KanbanFormat.prettyStatus("review"))
        assertEquals("Weird Status", KanbanFormat.prettyStatus("weird_status"))
    }

    @Test
    fun `status labels descriptions and terminality`() {
        assertEquals("Ready", KanbanStatus.label(KanbanStatus.READY))
        assertEquals("Review", KanbanStatus.label("review"))
        assertEquals("The dispatcher can claim these.", KanbanStatus.description(KanbanStatus.READY))
        assertNull(KanbanStatus.description("review"))
        assertTrue(KanbanStatus.isKnown(KanbanStatus.RUNNING))
        assertFalse(KanbanStatus.isKnown("review"))
        assertTrue(KanbanStatus.isTerminal(KanbanStatus.DONE))
        assertTrue(KanbanStatus.isTerminal(KanbanStatus.ARCHIVED))
        assertFalse(KanbanStatus.isTerminal(KanbanStatus.READY))
    }

    @Test
    fun `staleness thresholds`() {
        assertEquals(
            Staleness.Level.WARNING,
            KanbanStaleness.of(task(KanbanStatus.RUNNING, started = 601.0))?.level,
        )
        assertEquals(
            "slow",
            KanbanStaleness.of(task(KanbanStatus.RUNNING, started = 601.0))?.label,
        )
        assertEquals(
            Staleness.Level.ERROR,
            KanbanStaleness.of(task(KanbanStatus.RUNNING, started = 3601.0))?.level,
        )
        assertEquals(
            "stale",
            KanbanStaleness.of(task(KanbanStatus.RUNNING, started = 3601.0))?.label,
        )

        assertEquals(
            "blocked 2h",
            KanbanStaleness.of(task(KanbanStatus.BLOCKED, created = 7201.0))?.label,
        )
        assertEquals(
            Staleness.Level.WARNING,
            KanbanStaleness.of(task(KanbanStatus.BLOCKED, created = 7201.0))?.level,
        )
        assertEquals(
            Staleness.Level.ERROR,
            KanbanStaleness.of(task(KanbanStatus.BLOCKED, created = 86401.0))?.level,
        )
        assertEquals(
            "stale",
            KanbanStaleness.of(task(KanbanStatus.BLOCKED, created = 86401.0))?.label,
        )

        assertEquals(
            "waiting 1h",
            KanbanStaleness.of(task(KanbanStatus.READY, created = 3601.0))?.label,
        )
        assertEquals(
            Staleness.Level.WARNING,
            KanbanStaleness.of(task(KanbanStatus.READY, created = 3601.0))?.level,
        )
        assertNull(KanbanStaleness.of(task(KanbanStatus.READY, created = 600.0)))
    }

    private fun task(
        status: String,
        created: Double? = 100_000.0,
        started: Double? = null,
        completed: Double? = null,
    ) = KanbanTaskDto(
        id = "t_1",
        status = status,
        ageSeconds = KanbanAgeDto(
            createdAgeSeconds = created,
            startedAgeSeconds = started,
            timeToCompleteSeconds = completed,
        ),
    )
}
