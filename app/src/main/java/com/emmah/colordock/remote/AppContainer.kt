package com.emmah.colordock.remote

import android.content.Context
import com.emmah.colordock.remote.companion.CompanionClient
import com.emmah.colordock.remote.tv.LgTvClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val tv = LgTvClient(context)
    val dock = CompanionClient(tv, scope)
}
