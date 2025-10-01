package org.righteffort.vpnscheduler

import android.content.Context
import android.util.Log

object Logger {
    private var logBuffer: LogBuffer? = null

    fun init(context: Context) {
        logBuffer = LogBuffer.getInstance(context)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        logBuffer?.log("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        logBuffer?.log("I", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        logBuffer?.log("W", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message: ${throwable.message}"
        } else {
            message
        }
        logBuffer?.log("E", tag, fullMessage)
    }

    fun getLogs(): String {
        return logBuffer?.getLogsReversed() ?: "No logs available"
    }

    fun clearLogs() {
        logBuffer?.clear()
    }
}
