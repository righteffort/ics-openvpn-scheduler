package org.righteffort.openvpnscheduler

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import de.blinkt.openvpn.api.APIVpnProfile
import de.blinkt.openvpn.api.IOpenVPNAPIService

class RemoteVpn(private val context: Context) {
    private var mService: IOpenVPNAPIService? = null
    private var isBound = false
    
    companion object {
        private const val TAG = "RemoteVpn"
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(TAG, "Service connected")
            mService = IOpenVPNAPIService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.d(TAG, "Service disconnected")
            mService = null
        }
    }

    /**
     * Bind to the OpenVPN for Android service
     */
    fun bindService(): Boolean {
        if (isBound) return true
        
        val intent = Intent(IOpenVPNAPIService::class.java.name).apply {
            setPackage("de.blinkt.openvpn")
        }
        
        isBound = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
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
        val service = mService
        if (service == null) {
            Log.e(TAG, "Service not connected")
            return
        }

        try {
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
            Log.e(TAG, "Error executing action: ${action.command}", e)
        }
    }

    private fun start(uuid: String) {
        try {
            mService?.startProfile(uuid)
            Log.d(TAG, "Started profile: $uuid")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to start profile: $uuid", e)
        }
    }

    private fun stop() {
        try {
            mService?.disconnect()
            Log.d(TAG, "Disconnected VPN")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to disconnect VPN", e)
        }
    }

    private fun setDefault(uuid: String) {
        try {
            mService?.setDefaultProfile(uuid)
            Log.d(TAG, "Set default profile: $uuid")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to set default profile: $uuid", e)
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
