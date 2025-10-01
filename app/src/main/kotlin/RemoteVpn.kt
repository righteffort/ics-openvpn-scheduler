package org.righteffort.vpnscheduler

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import de.blinkt.openvpn.api.APIVpnProfile
import de.blinkt.openvpn.api.IOpenVPNAPIService

class RemoteVpn(
    private val context: Context,
    private val permissionLauncher: ActivityResultLauncher<Intent>
) {
    private var mService: IOpenVPNAPIService? = null
    private var isBound = false
    var onServiceReady: (() -> Unit)? = null

    companion object {
        private const val TAG = "VPNSchedulerRemoteVpn"
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Logger.i(TAG, "Service connected")
            mService = IOpenVPNAPIService.Stub.asInterface(service)

            try {
                // Request permission to use the API
                val permissionIntent = mService?.prepare(context.packageName)
                if (permissionIntent != null) {
                    Logger.i(TAG, "Permission required, starting permission request")
                    permissionLauncher.launch(permissionIntent)
                } else {
                    Logger.i(TAG, "Permission already granted")
                    onServiceReady?.invoke()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error requesting permission", e)
                onServiceReady?.invoke()
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Logger.i(TAG, "Service disconnected")
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
            context.unbindService(mConnection)
            isBound = false
            mService = null
        }
    }

    /**
     * Execute a VPN action
     */
    fun act(action: Action) {
        Logger.d(TAG, action.toString())
        val service = mService
        if (service == null) {
            Logger.e(TAG, "Service not connected")
            return
        }

        try {
            // TODO we don't need to do both of these, probably
            // TODO it would be better to arrange to execute the operation once
            // permission is granted
            // Check if we have permission to use the API
            val permissionIntent = service.prepare(context.packageName)
            if (permissionIntent != null) {
                Logger.e(TAG, "Permission required - need to request VPN API permission")
                permissionLauncher.launch(permissionIntent)
                return
            }

            // Also check VPN service permission
            val vpnPermissionIntent = service.prepareVPNService()
            if (vpnPermissionIntent != null) {
                Logger.e(TAG, "VPN permission required")
                permissionLauncher.launch(vpnPermissionIntent)
                return
            }

            // Now execute the actual command
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
        } catch (e: Exception) {
            Logger.e(TAG, "Error executing action: ${action.command}", e)
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

    private fun getUUID(profileName: String): String {
        val service = mService ?: throw IllegalStateException("Service not connected")

        try {
            val profiles = service.profiles
            for (profile in profiles) {
                if (profile.mName == profileName) {
                    return profile.mUUID
                }
            }
            throw IllegalArgumentException("Profile not found: $profileName")
        } catch (e: RemoteException) {
            throw RuntimeException("Failed to get profiles", e)
        }
    }
}
