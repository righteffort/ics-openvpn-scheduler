[Check when network reconnects](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)
Worker should not assume service is ready. Probably just retry with backoff.
Avoid 'immediate' work from clashing, probably just do enqueueUniqueWork
