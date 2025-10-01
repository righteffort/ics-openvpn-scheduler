package org.righteffort.vpnscheduler

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class LogBuffer private constructor(private val context: Context) {
    private val maxSize = 65536
    private val buffer = StringBuilder()
    private val lock = ReentrantReadWriteLock()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    companion object {
        @Volatile
        private var INSTANCE: LogBuffer? = null
        private const val LOG_FILE = "app_logs.txt"
        
        fun getInstance(context: Context): LogBuffer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LogBuffer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    init {
        loadFromFile()
    }
    
    fun log(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "$timestamp $level/$tag: $message\n"
        
        lock.write {
            buffer.append(logEntry)
            
            // Trim buffer if it exceeds max size
            if (buffer.length > maxSize) {
                val excess = buffer.length - maxSize
                val newlineIndex = buffer.indexOf('\n', excess)
                if (newlineIndex != -1) {
                    buffer.delete(0, newlineIndex + 1)
                } else {
                    buffer.delete(0, excess)
                }
            }
        }
        
        saveToFile()
    }
    
    fun getLogs(): String {
        return lock.read {
            buffer.toString()
        }
    }
    
    fun getLogsReversed(): String {
        return lock.read {
            buffer.toString().lines()
                .filter { it.isNotBlank() }
                .reversed()
                .joinToString("\n")
        }
    }
    
    fun clear() {
        lock.write {
            buffer.clear()
        }
        saveToFile()
    }
    
    private fun saveToFile() {
        try {
            val logFile = File(context.filesDir, LOG_FILE)
            lock.read {
                logFile.writeText(buffer.toString())
            }
        } catch (e: Exception) {
            // Can't log this error to our own buffer, so just ignore
        }
    }
    
    private fun loadFromFile() {
        try {
            val logFile = File(context.filesDir, LOG_FILE)
            if (logFile.exists()) {
                val content = logFile.readText()
                lock.write {
                    buffer.append(content)
                    // Ensure we don't exceed max size on load
                    if (buffer.length > maxSize) {
                        val excess = buffer.length - maxSize
                        val newlineIndex = buffer.indexOf('\n', excess)
                        if (newlineIndex != -1) {
                            buffer.delete(0, newlineIndex + 1)
                        } else {
                            buffer.delete(0, excess)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Can't log this error, so just start with empty buffer
        }
    }
}
