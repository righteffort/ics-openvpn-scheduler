package org.righteffort.vpnscheduler

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

// TOOD: don't do the fancy stuff, revert that commit.

object Logger {

    enum class Level(val value: Int) {
        @Suppress("unused")
        V(2),
        D(3),
        I(4),
        W(5),
        E(6),
        @Suppress("unused")
        A(7);
    }

    private data class LogRecord(
        val level: Level,
        val tag: String,
        val message: String
    )

    private data class LogState(
        val lastRecord: LogRecord?,
        val lastResult: Int,
        val elidedCount: Int
    )

    private lateinit var logBuffer: LogBuffer
    private val logStateAtomic = AtomicReference(LogState(null, 0, 0))

    fun init(context: Context) {
        logBuffer = LogBuffer.getInstance(context)
    }

    fun println(level: Level, tag: String, message: String): Int {
        val record = LogRecord(level, tag, message)

        while (true) {
            val currentState = logStateAtomic.get()

            if (record == currentState.lastRecord) {
                // Try to increment elided count
                val newState = currentState.copy(elidedCount = currentState.elidedCount + 1)
                if (logStateAtomic.compareAndSet(currentState, newState)) {
                    return currentState.lastResult // Return the cached result
                }
                // CAS failed, retry
                continue
            } else {
                // Different message - log it to both Android and LogBuffer
                val result = Log.println(level.value, tag, message)
                logBuffer.log(level.name, tag, message)

                // Log elided count if there was one
                if (currentState.elidedCount > 0) {
                    val elidedMsg =
                        "Previous message repeated ${currentState.elidedCount} more time(s)"
                    Log.println(level.value, tag, elidedMsg)
                    logBuffer.log(level.name, tag, elidedMsg)
                }

                // Try to update state
                val newState = LogState(record, result, 0)
                logStateAtomic.compareAndSet(currentState, newState)
                // Don't retry CAS here - if it fails, next call will handle it

                return result
            }
        }
    }

    fun d(tag: String, message: String): Int {
        return println(Level.D, tag, message)
    }

    fun i(tag: String, message: String): Int {
        return println(Level.I, tag, message)
    }

    fun w(tag: String, message: String): Int {
        return println(Level.W, tag, message)
    }

    fun e(tag: String, message: String, tr: Throwable? = null): Int {
        if (tr == null) {
            return println(Level.E, tag, message)
        }
        val result = Log.e(tag, message, tr)
        val fullMessage = "$message: ${tr.message}"
        logBuffer.log(Level.E.name, tag, fullMessage)
        return result
    }

    fun getLogs(): String {
        return logBuffer.getLogsReversed()
    }

    fun clearLogs() {
        logBuffer.clear()
    }
}
