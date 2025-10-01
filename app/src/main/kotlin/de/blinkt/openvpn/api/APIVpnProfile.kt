package de.blinkt.openvpn.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class APIVpnProfile(val mUUID: String, val mName: String, val mUserEditable: Boolean) : Parcelable
