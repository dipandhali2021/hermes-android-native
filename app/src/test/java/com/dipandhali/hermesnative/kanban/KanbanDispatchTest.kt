package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.domain.kanban.KanbanDispatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanDispatchTest {

    private val json = Json

    @Test
    fun `summary renders real buckets, tuples, and specials`() {
        val result = json.parseToJsonElement(
            """
            {
              "spawned": [["t1", "default", "/w"]],
              "skipped_unassigned": ["t2"],
              "promoted": 2,
              "reclaimed": 1,
              "skipped_locked": true,
              "memory_pressure": "critical",
              "unknown_bucket": ["x"],
              "crashed": [],
              "timed_out": [],
              "respawn_guarded": false,
              "reconciled_orphans": []
            }
            """.trimIndent(),
        ).jsonObject

        val summary = KanbanDispatch.summary(result, dryRun = true)
        assertTrue(summary.dryRun)
        assertTrue(summary.locked)
        assertEquals("critical", summary.memoryPressure)
        assertFalse(summary.nothingToDo)
        assertEquals(5, summary.rows.size)

        val rows = summary.rows.associateBy { it.key }
        rows["spawned"].let { row ->
            assertEquals("Spawned", row?.label)
            assertEquals(1, row?.count)
            assertEquals("t1", row?.text)
            assertEquals(listOf("t1 — default — /w"), row?.details)
        }
        rows["skipped_unassigned"].let { row ->
            assertEquals("Skipped — unassigned", row?.label)
            assertEquals(1, row?.count)
            assertEquals("t2", row?.text)
            assertEquals(listOf("t2"), row?.details)
        }
        rows["promoted"].let { row ->
            assertEquals("Promoted", row?.label)
            assertEquals(2, row?.count)
            assertNull(row?.text)
            assertTrue(row?.details?.isEmpty() == true)
        }
        rows["reclaimed"].let { row ->
            assertEquals("Reclaimed", row?.label)
            assertEquals(1, row?.count)
        }
        rows["unknown_bucket"].let { row ->
            assertEquals("Unknown bucket", row?.label)
            assertEquals(1, row?.count)
            assertEquals("x", row?.text)
            assertEquals(listOf("x"), row?.details)
        }

        assertFalse(
            summary.rows.any {
                it.key in setOf(
                    "skipped_locked",
                    "memory_pressure",
                    "crashed",
                    "timed_out",
                    "respawn_guarded",
                    "reconciled_orphans",
                )
            },
        )
    }

    @Test
    fun `nothing to do only when no rows and no specials`() {
        val empty = KanbanDispatch.summary(JsonObject(emptyMap()), dryRun = false)
        assertFalse(empty.dryRun)
        assertTrue(empty.rows.isEmpty())
        assertTrue(empty.nothingToDo)

        val zeroOnly = json.parseToJsonElement(
            """{"dry_run": true, "promoted": 0, "spawned": []}""",
        ).jsonObject
        val zeroSummary = KanbanDispatch.summary(zeroOnly, dryRun = false)
        assertFalse("dryRun must come from the parameter", zeroSummary.dryRun)
        assertTrue(zeroSummary.rows.isEmpty())
        assertTrue(zeroSummary.nothingToDo)

        val unlocked = KanbanDispatch.summary(
            json.parseToJsonElement("""{"skipped_locked": false}""").jsonObject,
            dryRun = false,
        )
        assertFalse(unlocked.locked)
        assertTrue(unlocked.nothingToDo)

        val locked = KanbanDispatch.summary(
            json.parseToJsonElement("""{"skipped_locked": true}""").jsonObject,
            dryRun = false,
        )
        assertTrue(locked.locked)
        assertTrue(locked.rows.isEmpty())
        assertFalse(locked.nothingToDo)

        val pressure = KanbanDispatch.summary(
            json.parseToJsonElement("""{"memory_pressure": "elevated"}""").jsonObject,
            dryRun = false,
        )
        assertEquals("elevated", pressure.memoryPressure)
        assertTrue(pressure.rows.isEmpty())
        assertFalse(pressure.nothingToDo)
    }

    @Test
    fun `unknown and rate-limited keys keep their data`() {
        val result = json.parseToJsonElement(
            """
            {
              "future_counter": 3,
              "future_bucket": ["a", "b"],
              "rate_limited": ["t3"]
            }
            """.trimIndent(),
        ).jsonObject

        val summary = KanbanDispatch.summary(result, dryRun = false)
        assertFalse(summary.nothingToDo)
        val rows = summary.rows.associateBy { it.key }

        assertEquals("Future counter", rows["future_counter"]?.label)
        assertEquals(3, rows["future_counter"]?.count)
        assertNull(rows["future_counter"]?.text)

        assertEquals("Future bucket", rows["future_bucket"]?.label)
        assertEquals(2, rows["future_bucket"]?.count)
        assertEquals("a", rows["future_bucket"]?.text)
        assertEquals(listOf("a", "b"), rows["future_bucket"]?.details)

        assertEquals("Rate-limited", rows["rate_limited"]?.label)
        assertEquals(1, rows["rate_limited"]?.count)
        assertEquals("t3", rows["rate_limited"]?.text)
    }
}
