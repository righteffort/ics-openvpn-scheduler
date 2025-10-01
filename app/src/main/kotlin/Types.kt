package org.righteffort.vpnscheduler

import java.time.Instant

enum class Command {
    START,
    STOP,
    SET_DEFAULT,
    SET_DEFAULT_AND_START
}

// Public API: just the command payload (no timestamp).
data class Action(
    val command: Command,
    val arguments: List<String>
)

data class TimedAction(
    val timestamp: Instant,
    val action: Action
)
