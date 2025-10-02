[Check when network reconnects](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)
probably
Worker should not assume service is ready. Probably just retry with backoff.
Avoid 'immediate' work from clashing, probably just do enqueueUniqueWork
Change 'action' to 'state' other than insite RemoteVpn.kt
Avoid changing state if system is already in that state
Change repo name to vpn-scheduler

