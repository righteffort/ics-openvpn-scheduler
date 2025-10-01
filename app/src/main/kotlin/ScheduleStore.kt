package org.righteffort.vpnscheduler

import java.time.Instant

class ScheduleStore private constructor(
    private val actions: List<TimedAction>
) {
    /**
     * Return action with largest timestamp <= t, or null if none exists.
     */
    fun getActiveAction(t: Instant): TimedAction? {
        var result: TimedAction? = null
        for (action in actions) {
            if (action.timestamp <= t) {
                result = action
            } else {
                break
            }
        }
        return result
    }

    companion object {
        /**
         * Parse CSV where each non-blank line is:
         *   <ISO-8601 timestamp>,<command>[,<optional arg>,...]
         * Timestamps must be strictly ascending. Capitalized command must be a member of the Command enum.
         */
        fun fromCsv(csv: String): ScheduleStore {
            val records = mutableListOf<TimedAction>()
            var lastTimestamp = Instant.MIN

            for (line in csv.lineSequence().map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }) {
                val parts = line.split(',').map { it.trim() }
                require(parts.size >= 2) { "Command missing from `$line`" }
                val ts = Instant.parse(parts[0])
                if (ts < lastTimestamp) {
                    throw IllegalArgumentException("Timestamps must be ascending: $ts after $lastTimestamp")
                }
                val command = Command.valueOf(parts[1].uppercase())
                val args = parts.drop(2)

                // Validate argument count based on command type
                when (command) {
                    Command.START, Command.SET_DEFAULT, Command.SET_DEFAULT_AND_START -> {
                        require(args.size == 1) {
                            "Command $command requires exactly one argument, got ${args.size} in line: $line"
                        }
                    }

                    Command.STOP -> {
                        require(args.isEmpty()) {
                            "Command $command requires no arguments, got ${args.size} in line: $line"
                        }
                    }
                }
                val action = Action(command, args)
                records.add(TimedAction(ts, action))
                lastTimestamp = ts
            }
            return ScheduleStore(records.toList())
        }
    }

    fun toCsv(): String {
        return actions.joinToString("\n") { timedAction ->
            (listOf(
                timedAction.timestamp.toString(),
                timedAction.action.command.toString()
            ) + timedAction.action.arguments).joinToString(",")
        }
    }
}
