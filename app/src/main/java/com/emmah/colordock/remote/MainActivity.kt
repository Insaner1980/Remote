package com.emmah.colordock.remote

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.window.SplashScreenView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvErrorCode
import com.emmah.colordock.remote.tv.TvState
import com.emmah.colordock.remote.tv.TvTarget
import com.emmah.colordock.remote.ui.AppTheme
import com.emmah.colordock.remote.ui.ConnectionErrorDialog
import com.emmah.colordock.remote.ui.ConnectionRoute
import com.emmah.colordock.remote.ui.ConnectionScreen
import com.emmah.colordock.remote.ui.DockRoute
import com.emmah.colordock.remote.ui.DockScreen
import com.emmah.colordock.remote.ui.DockViewModel
import com.emmah.colordock.remote.ui.RemoteAppShell
import com.emmah.colordock.remote.ui.RemoteRoute
import com.emmah.colordock.remote.ui.RemoteScreen
import com.emmah.colordock.remote.ui.RemoteTab
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.RemoteViewModel
import com.emmah.colordock.remote.ui.ThemeSettings
import com.emmah.colordock.remote.ui.ThemeViewModel
import com.emmah.colordock.remote.ui.TvConnectionDialog
import com.emmah.colordock.remote.ui.WallpaperRoute
import com.emmah.colordock.remote.ui.WallpaperScreen
import com.emmah.colordock.remote.ui.WallpaperViewModel
import com.emmah.colordock.remote.ui.asImageFile

class MainActivity : ComponentActivity() {
    private val appearance: ThemeViewModel by viewModels()
    private val model: RemoteViewModel by viewModels()
    private val wallpapers: WallpaperViewModel by viewModels()
    private val dock: DockViewModel by viewModels()
    private var requestedBefore by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    private var needsPermissionSettings by mutableStateOf(false)
    private var pendingAddress: String? = null
    private var pendingDiscovery = false
    private var launchSplash: SplashScreenView? = null
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            requestedBefore = true
            permissionDenied = !granted
            needsPermissionSettings =
                !granted && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK)
            if (granted) {

                if (pendingDiscovery) model.find() else pendingAddress?.let(model::connect)
            }
            pendingAddress = null
            pendingDiscovery = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSplash()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(INITIAL_NAVIGATION_BAR_COLOR),
        )
        window.isNavigationBarContrastEnforced = false
        requestedBefore = savedInstanceState?.getBoolean("permissionRequested") ?: false
        permissionDenied = savedInstanceState?.getBoolean("permissionDenied") ?: false
        pendingAddress = savedInstanceState?.getString("pendingAddress")
        pendingDiscovery = savedInstanceState?.getBoolean("pendingDiscovery") ?: false
        setContent { AppContent() }
    }

    @Composable
    private fun AppContent() {
        val savedTheme by appearance.theme.collectAsStateWithLifecycle()
        val theme = savedTheme ?: return
        val themeError by appearance.error.collectAsStateWithLifecycle()
        SideEffect {
            val style =
                if (theme ==
                    AppTheme.WarmLight
                ) {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            window.isNavigationBarContrastEnforced = false
        }
        RemoteTheme(theme) { NavigationContent(theme, themeError) }
    }

    // Keep lifecycle collections and saveable navigation state at the composition root.
    // Splitting their ownership would change subscription and restoration lifetimes.
    @Suppress("LongMethod")
    @Composable
    private fun NavigationContent(
        theme: AppTheme,
        themeError: String?,
    ) {
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        val openSettings = {
            model.release()
            settingsOpen = true
        }
        val state by model.state.collectAsStateWithLifecycle()
        val channels by model.channels.collectAsStateWithLifecycle()
        val tvApps by model.apps.collectAsStateWithLifecycle()
        val appIcons by model.appIcons.collectAsStateWithLifecycle()
        val savedAddress by model.savedAddress.collectAsStateWithLifecycle()
        val error by model.error.collectAsStateWithLifecycle()
        val discovered by model.discovered.collectAsStateWithLifecycle()
        val searching by model.searching.collectAsStateWithLifecycle()
        val dockState by dock.state.collectAsStateWithLifecycle()
        val dockUi by dock.ui.collectAsStateWithLifecycle()
        val wallpaperUi by wallpapers.ui.collectAsStateWithLifecycle()
        val thumbnails by wallpapers.thumbnails.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(RemoteTab.Remote) }
        val picker =
            rememberLauncherForActivityResult(
                ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKER_IMAGES),
                wallpapers::select,
            )
        LaunchedEffect(tab, state.connection) {
            if (tab != RemoteTab.Remote && state.connection == TvConnectionState.Ready) wallpapers.join()
        }
        LaunchedEffect(dockState.snapshot?.current?.id, dockState.snapshot?.current?.thumbnailHash) {
            dockState.snapshot?.current?.let(wallpapers::thumbnail)
        }
        var connectionSheet by rememberSaveable { mutableStateOf(false) }
        val forgetTv = {
            connectionSheet = false
            wallpapers.abandon()
            model.forget()
        }
        var invalidAddress by rememberSaveable { mutableStateOf(false) }
        val displayedState =
            when {
                invalidAddress -> {
                    TvState(TvConnectionState.Failed, getString(R.string.invalid_address))
                }

                permissionDenied -> {
                    TvState(
                        TvConnectionState.PermissionRequired,
                        getString(R.string.permission_explanation),
                    )
                }

                else -> {
                    state
                }
            }
        RemoteAppShell(tab, { next ->
            model.release()
            tab = next
        }) { screenModifier ->
            when {
                tab == RemoteTab.Wallpapers -> {
                    WallpaperRoute(
                        dockState,
                        wallpaperUi,
                        thumbnails,
                        { model },
                        { wallpapers },
                        {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        openSettings,
                        screenModifier,
                    )
                }

                tab == RemoteTab.Dock -> {
                    DockRoute(dockState, dockUi, thumbnails, { model }, { dock }, openSettings, screenModifier)
                }

                state.connection == TvConnectionState.Ready -> {
                    RemoteRoute(
                        state,
                        channels,
                        { model },
                        tvApps,
                        appIcons,
                        { connectionSheet = true },
                        openSettings,
                        screenModifier,
                    )
                }

                else -> {
                    ConnectionRoute(
                        state,
                        displayedState,
                        needsPermissionSettings,
                        savedAddress,
                        searching,
                        discovered,
                        forgetTv,
                        { address ->
                            invalidAddress = TvTarget.parse(address) == null
                            if (!invalidAddress) connectWithPermission(address)
                        },
                        model::disconnect,
                        {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    "package:$packageName".toUri(),
                                ),
                            )
                        },
                        ::findWithPermission,
                        openSettings,
                        screenModifier,
                    )
                }
            }
        }
        if (settingsOpen) {
            ThemeSettings(theme, appearance::select, {
                settingsOpen = false
            }, themeError)
        }
        if (connectionSheet) {
            TvConnectionDialog(
                state,
                { connectionSheet = false },
                { savedAddress?.let(model::connect) },
                forgetTv,
            )
        }
        error?.let { message -> ConnectionErrorDialog(message, model::clearError) }
    }

    private fun connectWithPermission(address: String) {
        if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionDenied = false
            model.connect(address)
        } else {
            pendingAddress = address
            permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    private fun findWithPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            model.find()
        } else {
            pendingDiscovery = true
            permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
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
                val title = createSplashTitle(splash, density)
                val easeOut = PathInterpolator(SPLASH_EASE_OUT_START, 1f, SPLASH_EASE_OUT_END, 1f)
                icon?.apply {
                    scaleX = SPLASH_INITIAL_SCALE
                    scaleY = SPLASH_INITIAL_SCALE
                    translationY = SPLASH_OFFSET_DP * density
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(SPLASH_ICON_DURATION_MS)
                        .setInterpolator(easeOut)
                        .start()
                }
                title
                    .animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(SPLASH_TITLE_DELAY_MS)
                    .setDuration(SPLASH_FADE_DURATION_MS)
                    .setInterpolator(easeOut)
                    .start()
                splash
                    .animate()
                    .alpha(0f)
                    .setStartDelay(SPLASH_EXIT_DELAY_MS)
                    .setDuration(SPLASH_FADE_DURATION_MS)
                    .setInterpolator(easeOut)
                    .withEndAction {
                        splash.remove()
                        launchSplash = null
                    }.start()
            }
        }
    }

    private fun createSplashTitle(
        splash: SplashScreenView,
        density: Float,
    ): TextView {
        val title =
            TextView(this).apply {
                text = getString(R.string.app_name)
                textSize = SPLASH_TITLE_SIZE_SP
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.launch_text))
                gravity = Gravity.CENTER
                alpha = 0f
                translationY = SPLASH_OFFSET_DP * density
            }
        splash.addView(
            title,
            FrameLayout
                .LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ).apply {
                    topMargin = (splash.iconView?.bottom ?: splash.height / 2) + (SPLASH_TITLE_GAP_DP * density).toInt()
                },
        )
        return title
    }

    override fun onStart() {
        super.onStart()
        model.foreground()
        if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            model.disconnect()
        } else {
            permissionDenied = false
        }
        needsPermissionSettings =
            permissionDenied && requestedBefore &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    override fun onStop() {
        launchSplash?.apply {
            animate().cancel()
            iconView?.animate()?.cancel()
            remove()
        }
        launchSplash = null
        if (!isChangingConfigurations) {
            wallpapers.background()
            model.background()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionRequested", requestedBefore)
        outState.putBoolean("permissionDenied", permissionDenied)
        outState.putString("pendingAddress", pendingAddress)
        outState.putBoolean("pendingDiscovery", pendingDiscovery)
        super.onSaveInstanceState(outState)
    }
}

private const val INITIAL_NAVIGATION_BAR_COLOR = -14343134
private const val MAX_PICKER_IMAGES = 10
private const val SPLASH_EASE_OUT_START = 0.23f
private const val SPLASH_EASE_OUT_END = 0.32f
private const val SPLASH_INITIAL_SCALE = 0.94f
private const val SPLASH_OFFSET_DP = 8f
private const val SPLASH_TITLE_GAP_DP = 16f
private const val SPLASH_TITLE_SIZE_SP = 28f
private const val SPLASH_ICON_DURATION_MS = 250L
private const val SPLASH_FADE_DURATION_MS = 200L
private const val SPLASH_TITLE_DELAY_MS = 40L
private const val SPLASH_EXIT_DELAY_MS = 300L
