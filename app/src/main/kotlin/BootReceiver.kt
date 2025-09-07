package org.righteffort.vpnscheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            Log.i("VPNScheduler", "Boot completed - restarting VPN scheduler")

            val workRequest = PeriodicWorkRequestBuilder<VPNScheduleWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "vpn_schedule_check",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            val immediateWork = OneTimeWorkRequestBuilder<VPNScheduleWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(immediateWork)
        }
    }
}
