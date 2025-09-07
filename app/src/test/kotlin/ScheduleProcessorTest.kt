package org.righteffort.vpnscheduler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.json.JSONArray
import java.time.LocalDateTime

class ScheduleProcessorTest {

    private val processor = ScheduleProcessor()

    @Test
    fun testParseValidSchedule() {
        val json = JSONArray("""[
            ["2025-09-01T06:00:00", "start:work-vpn"],
            ["2025-09-01T18:00:00", "stop"],
            ["2025-09-02", "start:home-vpn"]
        ]""")

        val entries = processor.parseSchedule(json)

        assertEquals(3, entries.size)
        assertEquals(LocalDateTime.of(2025, 9, 1, 6, 0, 0), entries[0].time)
        assertEquals("start:work-vpn", entries[0].action)
        assertEquals(LocalDateTime.of(2025, 9, 1, 18, 0, 0), entries[1].time)
        assertEquals("stop", entries[1].action)
        assertEquals(LocalDateTime.of(2025, 9, 2, 0, 0, 0), entries[2].time)
        assertEquals("start:home-vpn", entries[2].action)
    }

    @Test
    fun testParseScheduleIgnoresInvalidEntries() {
        val json = JSONArray("""[
            ["2025-09-01T06:00:00", "start:work-vpn"],
            ["invalid-date", "stop"],
            ["2025-09-01T18:00:00", "invalid-action"],
            ["2025-09-02", "start:"]
        ]""")

        val entries = processor.parseSchedule(json)

        assertEquals(1, entries.size)
        assertEquals("start:work-vpn", entries[0].action)
    }

    @Test
    fun testDetermineCurrentActionBasic() {
        val entries = listOf(
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 6, 0), "start:work-vpn"),
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 18, 0), "stop")
        )

        // Before any entries
        val before = LocalDateTime.of(2025, 9, 1, 5, 0)
        assertNull(processor.determineCurrentAction(entries, before))

        // Between entries
        val between = LocalDateTime.of(2025, 9, 1, 12, 0)
        assertEquals("start:work-vpn", processor.determineCurrentAction(entries, between))

        // After all entries
        val after = LocalDateTime.of(2025, 9, 1, 20, 0)
        assertEquals("stop", processor.determineCurrentAction(entries, after))
    }

    @Test
    fun testDetermineCurrentActionExactTiming() {
        val entries = listOf(
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 6, 0), "start:work-vpn")
        )

        // Exactly at the time
        val exact = LocalDateTime.of(2025, 9, 1, 6, 0)
        assertEquals("start:work-vpn", processor.determineCurrentAction(entries, exact))

        // One second before
        val before = LocalDateTime.of(2025, 9, 1, 5, 59, 59)
        assertNull(processor.determineCurrentAction(entries, before))

        // One second after
        val after = LocalDateTime.of(2025, 9, 1, 6, 0, 1)
        assertEquals("start:work-vpn", processor.determineCurrentAction(entries, after))
    }

    @Test
    fun testDetermineCurrentActionMostRecent() {
        val entries = listOf(
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 6, 0), "start:work-vpn"),
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 8, 0), "stop"),
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 10, 0), "start:home-vpn"),
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 12, 0), "stop")
        )

        val current = LocalDateTime.of(2025, 9, 1, 11, 0)
        assertEquals("start:home-vpn", processor.determineCurrentAction(entries, current))
    }

    @Test
    fun testDetermineCurrentActionNoValidEntries() {
        val entries = emptyList<ScheduleEntry>()
        val current = LocalDateTime.now()
        assertNull(processor.determineCurrentAction(entries, current))
    }

    @Test
    fun testDetermineCurrentActionAllFutureEntries() {
        val entries = listOf(
            ScheduleEntry(LocalDateTime.of(2025, 12, 1, 6, 0), "start:work-vpn"),
            ScheduleEntry(LocalDateTime.of(2025, 12, 1, 18, 0), "stop")
        )

        val current = LocalDateTime.of(2025, 9, 1, 12, 0)
        assertNull(processor.determineCurrentAction(entries, current))
    }

    @Test
    fun testIsValidAction() {
        assertTrue(processor.isValidAction("stop"))
        assertTrue(processor.isValidAction("start:work-vpn"))
        assertTrue(processor.isValidAction("start:home-vpn-123"))

        assertFalse(processor.isValidAction("start"))
        assertFalse(processor.isValidAction("start:"))
        assertFalse(processor.isValidAction("invalid"))
        assertFalse(processor.isValidAction(""))
        assertFalse(processor.isValidAction("connect:vpn"))
    }

    @Test
    fun testParseISODateTime() {
        // Valid datetime formats
        assertEquals(
            LocalDateTime.of(2025, 9, 1, 6, 0, 0),
            processor.parseISODateTime("2025-09-01T06:00:00")
        )

        assertEquals(
            LocalDateTime.of(2025, 9, 1, 14, 30, 45),
            processor.parseISODateTime("2025-09-01T14:30:45")
        )

        // Date only format
        assertEquals(
            LocalDateTime.of(2025, 9, 21, 0, 0, 0),
            processor.parseISODateTime("2025-09-21")
        )

        // Invalid formats
        assertNull(processor.parseISODateTime("invalid-date"))
        assertNull(processor.parseISODateTime("2025/09/01"))
        assertNull(processor.parseISODateTime("2025-09-01 06:00:00"))
        assertNull(processor.parseISODateTime(""))
    }

    @Test
    fun testComplexScheduleScenarioMonotonic() {
        val json = JSONArray("""[
            ["2025-09-01T06:00:00", "start:work-vpn"],
            ["2025-09-01T12:00:00", "stop"],
            ["2025-09-01T13:00:00", "start:work-vpn"],
            ["2025-09-01T18:00:00", "stop"],
            ["2025-09-01T19:00:00", "start:home-vpn"],
            ["2025-09-01T23:00:00", "stop"]
        ]""")

        val entries = processor.parseSchedule(json)
        assertEquals(6, entries.size)

        // Verify all entries are in monotonic order
        for (i in 1 until entries.size) {
            assertTrue(entries[i].time.isAfter(entries[i-1].time), "Entry $i should be after entry ${i-1}")
        }

        // Test various times throughout the day - logic should be simpler now
        assertEquals(null, processor.determineCurrentAction(entries, LocalDateTime.of(2025, 9, 1, 5, 0)))
        assertEquals("start:work-vpn", processor.determineCurrentAction(entries, LocalDateTime.of(2025, 9, 1, 10, 0)))
        assertEquals("stop", processor.determineCurrentAction(entries, LocalDateTime.of(2025, 9, 1, 12, 30)))
        assertEquals("start:work-vpn", processor.determineCurrentAction(entries, LocalDateTime.of(2025, 9, 1, 15, 0)))
        assertEquals("stop", processor.determineCurrentAction(entries, LocalDateTime.of(2025, 9, 1, 18, 30)))
        assertEquals("start:home-vpn", processor.determineCurrentAction(entries, LocalDateTime.of(2025, 9, 1, 21, 0)))
        assertEquals("stop", processor.determineCurrentAction(entries, LocalDateTime.of(2025, 9, 2, 1, 0)))
    }

    @Test
    fun testDetermineCurrentActionSimplified() {
        // With monotonic ordering, we can test the simplified reverse iteration
        val entries = listOf(
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 6, 0), "start:work-vpn"),
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 12, 0), "stop"),
            ScheduleEntry(LocalDateTime.of(2025, 9, 1, 18, 0), "start:home-vpn")
        )

        // Test that it finds the most recent applicable entry efficiently
        val current = LocalDateTime.of(2025, 9, 1, 15, 0)
        assertEquals("stop", processor.determineCurrentAction(entries, current))

        // Test edge case - exactly at a transition time
        val exactTime = LocalDateTime.of(2025, 9, 1, 18, 0)
        assertEquals("start:home-vpn", processor.determineCurrentAction(entries, exactTime))
    }
}
