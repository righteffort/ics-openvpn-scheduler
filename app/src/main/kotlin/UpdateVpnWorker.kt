package org.righteffort.vpnscheduler

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateVpnWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "VPNSchedulerUpdateVpnWorker"
    }

    // private val scheduleProcessor = ScheduleProcessor()

    override fun doWork(): Result {
        Logger.i(TAG, "Background schedule check triggered")
        return Result.success()
        /*
        return try {

            val scheduleJson = fetchSchedule()
            val entries = scheduleProcessor.parseSchedule(scheduleJson)
            val currentAction = scheduleProcessor.determineCurrentAction(entries, LocalDateTime.now())

            if (currentAction != null) {
                applyAction(currentAction)
            }

            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, "Background worker failed", e)
            Result.retry()
        }
    */
    }
    /*
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
                    Logger.i(TAG, "Applied action: stop")
                }
                action.startsWith("start:") -> {
                    val profileName = action.substring(6)
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.setClassName("de.blinkt.openvpn", "de.blinkt.openvpn.api.ConnectVPN")
                    intent.putExtra("de.blinkt.openvpn.api.profileName", profileName)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    Logger.i(TAG, "Applied action: start:$profileName")
                }
            }
        }
        */
}