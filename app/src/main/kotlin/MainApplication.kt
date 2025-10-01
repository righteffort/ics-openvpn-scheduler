package org.righteffort.vpnscheduler

import android.app.Application
import android.os.StrictMode

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
		    .penaltyDeath()
                    .build()
            )

            // Detect leaked closables, leaked registrations, etc.
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
		    .penaltyDeath()
                    .build()
            )
        }
    }
}
