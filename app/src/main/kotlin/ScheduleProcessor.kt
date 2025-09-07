package org.righteffort.vpnscheduler

import org.json.JSONArray
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ScheduleEntry(val time: LocalDateTime, val action: String)

class ScheduleProcessor {

    fun parseSchedule(scheduleJson: JSONArray): List<ScheduleEntry> {
        val entries = mutableListOf<ScheduleEntry>()
        var lastTime: LocalDateTime? = null

        for (i in 0 until scheduleJson.length()) {
            try {
                val entry = scheduleJson.getJSONArray(i)
                val timeStr = entry.getString(0)
                val action = entry.getString(1)

                if (!isValidAction(action)) {
                    continue
                }

                val time = parseISODateTime(timeStr)
                if (time != null) {
                    // Validate monotonic ordering
                    if (lastTime != null && !time.isAfter(lastTime)) {
                        throw IllegalArgumentException("Schedule entries must be in monotonic increasing time order. Entry at $timeStr is not after previous entry.")
                    }

                    entries.add(ScheduleEntry(time, action))
                    lastTime = time
                }
            } catch (e: Exception) {
                // Re-throw ordering violations, skip other invalid entries
                if (e is IllegalArgumentException) {
                    throw e
                }
            }
        }

        return entries
    }

    fun determineCurrentAction(entries: List<ScheduleEntry>, currentTime: LocalDateTime): String? {
        // Since entries are guaranteed to be in monotonic increasing order,
        // we can iterate from the end to find the most recent applicable entry
        for (i in entries.size - 1 downTo 0) {
            val entry = entries[i]
            if (entry.time.isBefore(currentTime) || entry.time.isEqual(currentTime)) {
                return entry.action
            }
        }

        return null // No applicable entry found
    }

    fun isValidAction(action: String): Boolean {
        return action == "stop" || (action.startsWith("start:") && action.length > 6)
    }

    fun parseISODateTime(timeStr: String): LocalDateTime? {
        return try {
            when {
                // ISO8601 datetime: "2025-09-01T06:00:00"
                timeStr.contains("T") -> {
                    LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                // ISO8601 date only: "2025-09-21" -> convert to midnight
                timeStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                    LocalDateTime.parse(timeStr + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
