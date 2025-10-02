package org.righteffort.vpnscheduler

import android.app.Application
import android.os.StrictMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class MainApplication : Application() {

    companion object {
        private const val CONFIG_FILE = "schedule_config.csv"
        private var instance: MainApplication? = null

        fun getInstance(): MainApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    private var _scheduleStore: ScheduleStore? = null
    private lateinit var _remoteVpn: RemoteVpn
    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val scheduleStore: ScheduleStore?
        get() = _scheduleStore

    val remoteVpn: RemoteVpn
        get() = _remoteVpn

    override fun onCreate() {
        super.onCreate()
        instance = this
        Logger.init(this)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }

        // Initialize RemoteVpn with no permission handler (background-only mode)
        _remoteVpn = RemoteVpn(this, null)

        // Load saved schedule configuration on background thread
        loadScheduleStoreAsync()
    }

    fun updateScheduleStore(scheduleStore: ScheduleStore) {
        _scheduleStore = scheduleStore
        saveScheduleStore(scheduleStore)
    }

    private fun loadScheduleStoreAsync() {
        // Use coroutines to avoid StrictMode violations
        applicationScope.launch {
            try {
                val configFile = File(filesDir, CONFIG_FILE)
                if (configFile.exists()) {
                    val content = configFile.readText()
                    _scheduleStore = ScheduleStore.fromCsv(content)
                    Logger.i(
                        "MainApplication",
                        "Loaded schedule configuration with ${_scheduleStore?.let { "data" } ?: "no data"}")
                }
            } catch (e: Exception) {
                // TODO can we avoid this branch, and then never worry about null _scheduleStore?
                Logger.e("MainApplication", "Failed to load schedule configuration", e)
            }
        }
    }

    private fun saveScheduleStore(scheduleStore: ScheduleStore) {
        // Save using coroutines to avoid StrictMode violations
        applicationScope.launch {
            try {
                val configFile = File(filesDir, CONFIG_FILE)
                configFile.writeText(scheduleStore.toCsv())
                Logger.i("MainApplication", "Saved schedule configuration")
            } catch (e: Exception) {
                Logger.e("MainApplication", "Failed to save schedule configuration", e)
            }
        }
    }
}
