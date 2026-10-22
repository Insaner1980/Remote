package com.emmah.colordock.remote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.window.SplashScreenView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import com.emmah.colordock.remote.tv.TvTarget
import com.emmah.colordock.remote.ui.ConnectionScreen
import com.emmah.colordock.remote.ui.RemoteViewModel
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.RemoteScreen
import com.emmah.colordock.remote.ui.*

class MainActivity : ComponentActivity() {
    private val appearance: ThemeViewModel by viewModels()
    private val model: RemoteViewModel by viewModels()
    private val wallpapers: WallpaperViewModel by viewModels()
    private val dock: DockViewModel by viewModels()
    private var requestedBefore by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    private var pendingAddress: String? = null
    private var pendingDiscovery = false
    private var launchSplash: SplashScreenView? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requestedBefore = true
        permissionDenied = !granted
        if (granted) {if(pendingDiscovery)model.find() else pendingAddress?.let(model::connect)}
        pendingAddress = null
        pendingDiscovery = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSplash()
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(37,36,34)))
        window.isNavigationBarContrastEnforced=false
        requestedBefore = savedInstanceState?.getBoolean("permissionRequested") ?: false
        pendingAddress=savedInstanceState?.getString("pendingAddress")
        pendingDiscovery=savedInstanceState?.getBoolean("pendingDiscovery")?:false
        setContent {
            val savedTheme by appearance.theme.collectAsStateWithLifecycle()
            val theme = savedTheme ?: return@setContent
            val themeError by appearance.error.collectAsStateWithLifecycle()
            SideEffect {
                val style = if (theme == AppTheme.WarmLight) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.isNavigationBarContrastEnforced = false
            }
            RemoteTheme(theme) {
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                val openSettings = { model.release(); settingsOpen = true }
                val state by model.state.collectAsStateWithLifecycle()
                val channels by model.channels.collectAsStateWithLifecycle()
                val savedAddress by model.savedAddress.collectAsStateWithLifecycle()
                val error by model.error.collectAsStateWithLifecycle()
                val discovered by model.discovered.collectAsStateWithLifecycle()
                val searching by model.searching.collectAsStateWithLifecycle()
                val dockState by dock.state.collectAsStateWithLifecycle()
                val dockUi by dock.ui.collectAsStateWithLifecycle()
                val wallpaperUi by wallpapers.ui.collectAsStateWithLifecycle()
                val thumbnails by wallpapers.thumbnails.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableStateOf(RemoteTab.Remote) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10),wallpapers::select)
                LaunchedEffect(tab,state.connection) {
                    if(tab!=RemoteTab.Remote&&state.connection==TvConnectionState.Ready)wallpapers.join()
                }
                LaunchedEffect(dockState.snapshot?.current?.id) {dockState.snapshot?.current?.let(wallpapers::thumbnail)}
                var connectionSheet by rememberSaveable { mutableStateOf(false) }
                var invalidAddress by rememberSaveable { mutableStateOf(false) }
                val displayedState = when {
                    invalidAddress -> TvState(TvConnectionState.Failed, getString(R.string.invalid_address))
                    permissionDenied -> TvState(TvConnectionState.PermissionRequired, getString(R.string.permission_explanation))
                    else -> state
                }
                RemoteAppShell(tab,{next->model.release();tab=next}) { screenModifier ->
                    if(tab==RemoteTab.Wallpapers) WallpaperScreen(
                        dockState,wallpaperUi,thumbnails,model::launchDock,wallpapers::join,wallpapers::source,{wallpapers.load(true)},wallpapers::thumbnail,wallpapers::detail,
                        {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},wallpapers::patch,wallpapers::imageAction,wallpapers::next,wallpapers::preparedFit,wallpapers::send,wallpapers::cancelUpload,wallpapers::abandon,screenModifier,onSettings=openSettings,
                    ) else if(tab==RemoteTab.Dock) DockScreen(dockState,dockUi,thumbnails[dockState.snapshot?.current?.id],model::launchDock,dock::join,dock::clock,dock::patch,dock::shelf,dock::query,dock::search,dock::location,screenModifier,onSettings=openSettings)
                    else if (state.connection == TvConnectionState.Ready) RemoteScreen(
                        state, channels, model::send, model::press, model::release, model::launchDock, model::tune,
                        onConnection = { connectionSheet = true }, modifier = screenModifier, onSettings = openSettings,
                    ) else ConnectionScreen(
                        state = displayedState,
                        needsSettings = permissionDenied && requestedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK),
                        onConnect = { address ->
                            invalidAddress = TvTarget.parse(address) == null
                            if (!invalidAddress) {
                                if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
                                    permissionDenied = false
                                    model.connect(address)
                                } else {
                                    pendingAddress = address
                                    permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                }
                            }
                        },
                        onCancel = model::disconnect,
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) },
                        modifier = screenModifier,
                        onAppSettings = openSettings,
                        initialAddress = savedAddress,
                        searching=searching,discovered=discovered,onFind={
                            if(checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)==PackageManager.PERMISSION_GRANTED)model.find()
                            else{pendingDiscovery=true;permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)}
                        },
                    )
                }
                if (settingsOpen) ThemeSettings(theme, appearance::select, { settingsOpen = false }, themeError)
                if (connectionSheet) AlertDialog(
                    onDismissRequest = { connectionSheet = false },
                    title = { Text(state.name) },
                    text = { Column {
                        Text(if (state.connection == TvConnectionState.Ready) "Connected" else "TV unavailable")
                        TextButton(onClick = { connectionSheet = false; savedAddress?.let(model::connect) }) { Text("Reconnect") }
                        TextButton(onClick = { connectionSheet = false; wallpapers.abandon();model.forget() }) { Text("Forget TV") }
                    } },
                    confirmButton = { TextButton(onClick = { connectionSheet = false }) { Text("Close") } },
                )
                error?.let { message -> AlertDialog(
                    onDismissRequest = model::clearError, title = { Text("Connection") }, text = { Text(message) },
                    confirmButton = { TextButton(onClick = model::clearError) { Text("Close") } },
                ) }
            }
        }
    }

    private fun configureSplash() {
        splashScreen.setOnExitAnimationListener { splash ->
            if (!ValueAnimator.areAnimatorsEnabled()) {
                splash.remove()
                return@setOnExitAnimationListener
            }
            launchSplash = splash
            splash.post {
                if (launchSplash !== splash) return@post
                val density = resources.displayMetrics.density
                val icon = splash.iconView
                val title = TextView(this).apply {
                    text = getString(R.string.app_name)
                    textSize = 28f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(getColor(R.color.launch_text))
                    gravity = Gravity.CENTER
                    alpha = 0f
                    translationY = 8f * density
                }
                splash.addView(title, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ).apply { topMargin = (icon?.bottom ?: splash.height / 2) + (16f * density).toInt() })
                val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)
                icon?.apply {
                    scaleX = 0.94f
                    scaleY = 0.94f
                    translationY = 8f * density
                    animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(250).setInterpolator(easeOut).start()
                }
                title.animate().alpha(1f).translationY(0f).setStartDelay(40).setDuration(200).setInterpolator(easeOut).start()
                splash.animate().alpha(0f).setStartDelay(300).setDuration(200).setInterpolator(easeOut).withEndAction {
                    splash.remove()
                    launchSplash = null
                }.start()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.foreground()
        if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            model.disconnect()
        } else permissionDenied = false
    }

    override fun onStop() {
        launchSplash?.apply { animate().cancel(); iconView?.animate()?.cancel(); remove() }
        launchSplash = null
        if (!isChangingConfigurations) { wallpapers.background(); model.background() }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionRequested", requestedBefore)
        outState.putString("pendingAddress",pendingAddress)
        outState.putBoolean("pendingDiscovery",pendingDiscovery)
        super.onSaveInstanceState(outState)
    }
}
