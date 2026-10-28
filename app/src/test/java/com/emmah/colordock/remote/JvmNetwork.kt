package com.emmah.colordock.remote

import android.net.ConnectivityManager
import android.net.NetworkInfo
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkInfo

/** Supplies the local network expected by the fake TV transport scenarios. */
class JvmNetwork : TestEnvironmentLifecyclePlugin {
    @Suppress("DEPRECATION")
    override fun onSetupApplicationState() {
        ShadowConnectivityManager().setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true,
            ),
        )
    }
}
