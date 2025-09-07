package org.righteffort.vpnscheduler

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.net.URL
import java.text.SimpleDateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var checkButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val scheduleProcessor = ScheduleProcessor()

    companion object {
        const val SCHEDULE_URL = "https://your-server.com/vpn-schedule.json"
        const val EXTRA_NAME = "de.blinkt.openvpn.api.profileName"
        const val TAG = "VPNScheduler"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        checkButton = findViewById(R.id.checkButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        checkButton.setOnClickListener {
            checkScheduleAndApply()
        }

        startButton.setOnClickListener {
            // Manual test - start VPN
            startVPN("your-profile-name")
        }

        stopButton.setOnClickListener {
            // Manual test - stop VPN
            stopVPN()
        }

        // Schedule periodic checks (every 30 minutes)
        schedulePeriodicCheck()

        // Check immediately on app start
        checkScheduleAndApply()
    }

    private fun schedulePeriodicCheck() {
        val workRequest = PeriodicWorkRequestBuilder<VPNScheduleWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vpn_schedule_check",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun checkScheduleAndApply() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduleJson = fetchSchedule()
                val entries = scheduleProcessor.parseSchedule(scheduleJson)
                val currentAction = scheduleProcessor.determineCurrentAction(entries, LocalDateTime.now())

                withContext(Dispatchers.Main) {
                    if (currentAction != null) {
                        updateStatus("Current action: $currentAction")
                        applyAction(currentAction)
                    } else {
                        updateStatus("No valid action found")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Error: ${e.message}")
                    Log.e(TAG, "Failed to check schedule", e)
                }
            }
        }
    }

    private suspend fun fetchSchedule(): JSONArray {
        return withContext(Dispatchers.IO) {
            val url = URL(SCHEDULE_URL)
            val response = url.readText()
            JSONArray(response)
        }
    }

    private fun applyAction(action: String) {
        when {
            action == "stop" -> stopVPN()
            action.startsWith("start:") -> {
                val profileName = action.substring(6)
                startVPN(profileName)
            }
        }
    }

    private fun startVPN(profileName: String) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.setClassName("de.blinkt.openvpn", "de.blinkt.openvpn.api.ConnectVPN")
            intent.putExtra(EXTRA_NAME, profileName)
            startActivity(intent)

            updateStatus("Started VPN profile: $profileName")
            Log.i(TAG, "Started VPN profile: $profileName")
        } catch (e: Exception) {
            updateStatus("Failed to start VPN: ${e.message}")
            Log.e(TAG, "Failed to start VPN", e)
            Toast.makeText(this, "Failed to start VPN - is ics-openvpn installed?", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVPN() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.setClassName("de.blinkt.openvpn", "de.blinkt.openvpn.api.DisconnectVPN")
            startActivity(intent)

            updateStatus("Stopped VPN")
            Log.i(TAG, "Stopped VPN")
        } catch (e: Exception) {
            updateStatus("Failed to stop VPN: ${e.message}")
            Log.e(TAG, "Failed to stop VPN", e)
        }
    }

    private fun updateStatus(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        statusText.text = "[$timestamp] $message"
    }
}
