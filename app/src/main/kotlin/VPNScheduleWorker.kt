package org.righteffort.vpnscheduler

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.json.JSONArray

class VPNScheduleWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val scheduleProcessor = ScheduleProcessor()

    override fun doWork(): Result {
        return try {
            Log.i("VPNScheduler", "Background schedule check triggered")

            val scheduleJson = fetchSchedule()
            val entries = scheduleProcessor.parseSchedule(scheduleJson)
            val currentAction = scheduleProcessor.determineCurrentAction(entries, LocalDateTime.now())

            if (currentAction != null) {
                applyAction(currentAction)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("VPNScheduler", "Background worker failed", e)
            Result.retry()
        }
    }

    private fun fetchSchedule(): JSONArray {
        val url = URL("https://your-server.com/vpn-schedule.json")
        val response = url.readText()
        return JSONArray(response)
    }

    private fun applyAction(action: String) {
        val context = applicationContext

        when {
            action == "stop" -> {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClassName("de.blinkt.openvpn", "de.blinkt.openvpn.api.DisconnectVPN")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.i("VPNScheduler", "Applied action: stop")
            }
            action.startsWith("start:") -> {
                val profileName = action.substring(6)
                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClassName("de.blinkt.openvpn", "de.blinkt.openvpn.api.ConnectVPN")
                intent.putExtra("de.blinkt.openvpn.api.profileName", profileName)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.i("VPNScheduler", "Applied action: start:$profileName")
            }
        }
    }
}
