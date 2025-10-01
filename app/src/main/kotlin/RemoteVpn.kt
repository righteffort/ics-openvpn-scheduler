package org.righteffort.vpnscheduler

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import de.blinkt.openvpn.api.IOpenVPNAPIService
import de.blinkt.openvpn.api.IOpenVPNStatusCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface PermissionHandler {
    fun registerForActivityResult(
        contract: ActivityResultContracts.StartActivityForResult,
        callback: (androidx.activity.result.ActivityResult) -> Unit
    ): ActivityResultLauncher<Intent>
}

class RemoteVpn(
    private val context: Context,
    permissionHandler: PermissionHandler
) {
    private var mService: IOpenVPNAPIService? = null
    private var isBound = false
    var onServiceReady: (() -> Unit)? = null

    // Pending action to execute after permission is granted
    private var pendingAction: (suspend () -> Unit)? = null

    private var permissionLauncher: ActivityResultLauncher<Intent>
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        private const val TAG = "VPNSchedulerRemoteVpn"
    }

    init {
        // Register permission launcher during initialization
        permissionLauncher = permissionHandler.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                onPermissionGranted()
            } else {
                onPermissionDenied()
            }
        }
    }

    private val statusCallback = object : IOpenVPNStatusCallback.Stub() {
        override fun newStatus(uuid: String?, state: String?, message: String?, level: String?) {
            Logger.i(
                TAG,
                "VPN Status - UUID=$uuid, State=$state, Message=$message, Level=$level"
            )
        }
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Logger.i(TAG, "Service connected")
            mService = IOpenVPNAPIService.Stub.asInterface(service)

            // Register status callback and notify service ready on background thread
            serviceScope.launch {
                executeWithPermission {
                    registerStatusCallbackSafely()
                }
                // Call onServiceReady on main thread after permission handling
                withContext(Dispatchers.Main) {
                    onServiceReady?.invoke()
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Logger.i(TAG, "Service disconnected")
            try {
                mService?.unregisterStatusCallback(statusCallback)
            } catch (e: Exception) {
                Logger.e(TAG, "Error unregistering callback", e)
            }
            mService = null
        }
    }

    /**
     * Bind to the OpenVPN for Android service
     */
    fun bindService(): Boolean {
        if (isBound) return true

        Logger.d(TAG, "service class name: ${IOpenVPNAPIService::class.java.name}")
        val intent = Intent(IOpenVPNAPIService::class.java.name).apply {
            setPackage("de.blinkt.openvpn")
        }
        Logger.d(TAG, "Intent action: ${intent.action}")
        isBound = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        /* this approach doesn't suffice
        if (isBound) {
           if (mService?.prepareVPNService() != null) {
                Logger.e(TAG, "Permission to control service missing")
                 unbindService()
                 }
        }
        */
        return isBound
    }

    /**
     * Unbind from the OpenVPN for Android service
     */
    fun unbindService() {
        if (isBound) {
            // Unregister callback on background thread to avoid StrictMode violations
            serviceScope.launch {
                try {
                    mService?.unregisterStatusCallback(statusCallback)
                    Logger.i(TAG, "Status callback unregistered")
                } catch (e: Exception) {
                    Logger.e(TAG, "Error unregistering callback during unbind", e)
                }
            }
            context.unbindService(mConnection)
            isBound = false
            mService = null
        }
    }

    /**
     * Execute an action with permission handling
     */
    private suspend fun executeWithPermission(action: suspend () -> Unit) {
        val service = mService
        if (service == null) {
            Logger.e(TAG, "Service not connected")
            return
        }

        try {
            // Check if we have permission to use the API
            val permissionIntent = service.prepare(context.packageName)
            if (permissionIntent == null) {
                // Already have permission, execute immediately
                Logger.d(TAG, "Already have permission")
                action()
            } else {
                // Need permission, stash action and request it
                Logger.i(TAG, "Permission required, storing action and requesting permission")
                pendingAction = action

                withContext(Dispatchers.Main) {
                    permissionLauncher.launch(permissionIntent)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking permissions", e)
        }
    }

    /**
     * Call this when permission is granted (from MainActivity's permission result handler)
     */
    fun onPermissionGranted() {
        Logger.i(TAG, "Permission granted")
        val action = pendingAction
        pendingAction = null

        if (action != null) {
            serviceScope.launch {
                try {
                    action()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error executing pending action", e)
                }
            }
        }
    }

    /**
     * Call this when permission is denied
     */
    fun onPermissionDenied() {
        Logger.w(TAG, "Permission denied")
        pendingAction = null
    }

    /**
     * Execute a VPN action
     */
    fun act(action: Action) {
        Logger.d(TAG, action.toString())

        serviceScope.launch {
            executeWithPermission {
                try {
                    // Check VPN service permission
                    val vpnPermissionIntent = mService?.prepareVPNService()
                    if (vpnPermissionIntent != null) {
                        Logger.e(TAG, "VPN permission required")
                        pendingAction = {
                            executeVpnCommand(action)
                        }

                        // Launch permission request on main thread
                        withContext(Dispatchers.Main) {
                            permissionLauncher.launch(vpnPermissionIntent)
                        }
                        return@executeWithPermission
                    }

                    // Execute the actual command
                    executeVpnCommand(action)
                } catch (e: Exception) {
                    Logger.e(TAG, "Error executing action: ${action.command}", e)
                }
            }
        }
    }

    private suspend fun executeVpnCommand(action: Action) {
        when (action.command) {
            Command.START -> {
                val uuid = getUUID(action.arguments[0])
                start(uuid)
            }

            Command.STOP -> {
                stop()
            }

            Command.SET_DEFAULT -> {
                val uuid = getUUID(action.arguments[0])
                setDefault(uuid)
            }

            Command.SET_DEFAULT_AND_START -> {
                val uuid = getUUID(action.arguments[0])
                setDefault(uuid)
                start(uuid)
            }
        }
    }

    private fun registerStatusCallbackSafely() {
        try {
            mService?.registerStatusCallback(statusCallback)
            Logger.i(TAG, "Status callback registered")
        } catch (e: Exception) {
            Logger.e(TAG, "Error registering status callback", e)
        }
    }

    private fun start(uuid: String) {
        try {
            mService?.startProfile(uuid)
            Logger.d(TAG, "Started profile: $uuid")
        } catch (e: RemoteException) {
            Logger.e(TAG, "Failed to start profile: $uuid", e)
        }
    }

    private fun stop() {
        try {
            mService?.disconnect()
            Logger.d(TAG, "Disconnected VPN")
        } catch (e: RemoteException) {
            Logger.e(TAG, "Failed to disconnect VPN", e)
        }
    }

    private fun setDefault(uuid: String) {
        try {
            mService?.setDefaultProfile(uuid)
            Logger.d(TAG, "Set default profile: $uuid")
        } catch (e: RemoteException) {
            Logger.e(TAG, "Failed to set default profile: $uuid", e)
        }
    }

    private suspend fun getUUID(profileName: String): String = withContext(Dispatchers.IO) {
        val service = mService ?: throw IllegalStateException("Service not connected")

        try {
            val profiles = service.profiles
            for (profile in profiles) {
                if (profile.mName == profileName) {
                    return@withContext profile.mUUID
                }
            }
            throw IllegalArgumentException("Profile not found: $profileName")
        } catch (e: RemoteException) {
            throw RuntimeException("Failed to get profiles", e)
        }
    }
}
