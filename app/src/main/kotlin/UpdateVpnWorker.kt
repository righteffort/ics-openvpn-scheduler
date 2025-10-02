package org.righteffort.vpnscheduler

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Instant

class UpdateVpnWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "VPNSchedulerUpdateVpnWorker"
    }

    override fun doWork(): Result {
        Logger.i(TAG, "Schedule check triggered")

        try {
            val app = MainApplication.getInstance()

            // Get schedule from application
            val scheduleStore = app.scheduleStore
            if (scheduleStore == null) {
                Logger.i(TAG, "No schedule configuration found")
                return Result.success()
            }

            // Get current time and find active action
            val now = Instant.now()
            val activeAction = scheduleStore.getActiveAction(now)
            if (activeAction == null) {
                Logger.i(TAG, "No applicable action for current time")
                return Result.success()
            }

            Logger.i(
                TAG,
                "Found active action: ${activeAction.action.command} at ${activeAction.timestamp}"
            )

            // Get RemoteVpn instance from application and execute action
            val remoteVpn = app.remoteVpn  // No null check needed
            remoteVpn.act(activeAction.action)

            return Result.success()

        } catch (e: Exception) {
            Logger.e(TAG, "Error during schedule check", e)
            return Result.failure()
        }
    }
}
