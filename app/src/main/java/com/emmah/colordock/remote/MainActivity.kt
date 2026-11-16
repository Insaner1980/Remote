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
import androidx.compose.runtime.remember
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
import com.emmah.colordock.remote.ui.ControlTarget
import com.emmah.colordock.remote.ui.ControlTargetLease
import com.emmah.colordock.remote.ui.ControlTargetViewModel
import com.emmah.colordock.remote.ui.DockRoute
import com.emmah.colordock.remote.ui.DockScreen
import com.emmah.colordock.remote.ui.DockViewModel
import com.emmah.colordock.remote.ui.ObserveCompanion
import com.emmah.colordock.remote.ui.RemoteAppShell
import com.emmah.colordock.remote.ui.RemoteRoute
import com.emmah.colordock.remote.ui.RemoteScreen
import com.emmah.colordock.remote.ui.RemoteTab
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.RemoteViewModel
import com.emmah.colordock.remote.ui.TargetAppShell
import com.emmah.colordock.remote.ui.TargetSelector
import com.emmah.colordock.remote.ui.ThemeSettings
import com.emmah.colordock.remote.ui.ThemeViewModel
import com.emmah.colordock.remote.ui.TvConnectionDialog
import com.emmah.colordock.remote.ui.WallpaperRoute
import com.emmah.colordock.remote.ui.WallpaperScreen
import com.emmah.colordock.remote.ui.WallpaperViewModel
import com.emmah.colordock.remote.ui.asImageFile
import com.emmah.colordock.remote.ui.displayedTvState
import com.emmah.colordock.remote.ui.mediacenter.MediaDestination
import com.emmah.colordock.remote.ui.mediacenter.MediacenterEntry
import com.emmah.colordock.remote.ui.mediacenter.MediacenterNavigation
import com.emmah.colordock.remote.ui.mediacenter.MediacenterSetupDialog
import com.emmah.colordock.remote.ui.mediacenter.MediacenterTargetPage
import com.emmah.colordock.remote.ui.mediacenter.MediacenterViewModel

class MainActivity : ComponentActivity() {
    private val targets: ControlTargetViewModel by viewModels()
    private val mediacenter: MediacenterViewModel by viewModels()
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
        val state by model.state.collectAsStateWithLifecycle()
        val targetState by targets.state.collectAsStateWithLifecycle()
        val repository = (application as ColorDockApplication).container.mediacenter
        val mcConnection by repository.state.collectAsStateWithLifecycle()
        LaunchedEffect(state.connection, mcConnection) { targets.observe(state.connection, mcConnection) }
        val lease = ControlTargetLease(targetState.selected, targetState.generation)
        val mediaSend =
            remember(repository, lease) {
                { request: com.emmah.colordock.remote.mediacenter.protocol.McRequest ->
                    var sent = false
                    targets.dispatch(lease) { sent = repository.send(request) }
                    sent
                }
            }
        var mcSetup by rememberSaveable { mutableStateOf(false) }
        val mediaNavigation = remember { MediacenterNavigation() }
        val retireContext: (() -> Unit) -> Unit = { open ->
            targets.openContext(lease, {
                model.cancelInput()
                mediacenter.closeSetup()
            }, open)
        }
        val openSettings = { retireContext { settingsOpen = true } }
        val openMcSetup = { retireContext { mcSetup = true } }
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
        ObserveCompanion(targetState.selected, tab, state.connection, dockState) { wallpapers }
        var connectionSheet by rememberSaveable { mutableStateOf(false) }
        val forgetTv = {
            connectionSheet = false
            wallpapers.abandon()
            model.forget()
        }
        var invalidAddress by rememberSaveable { mutableStateOf(false) }
        val displayedState = displayedTvState(state, invalidAddress, permissionDenied, resources)
        val selector: @Composable () -> Unit = {
            TargetSelector(targetState, state.name, { next ->
                targets.select(next) {
                    model.cancelInput()
                    mediacenter.closeSetup()
                    connectionSheet = false
                    settingsOpen = false
                    mcSetup = false
                }
            }, {
                retireContext {
                    if (targetState.selected == ControlTarget.LgTv) connectionSheet = true else mcSetup = true
                }
            }, compact = mediaNavigation.destination != MediaDestination.Remote)
        }
        TargetAppShell(
            targetState,
            tab,
            { next ->
                model.cancelInput()
                tab = next
            },
            mediacenter = { screenModifier ->
                MediacenterTargetPage(
                    repository,
                    screenModifier,
                    settings = {
                        com.emmah.colordock.remote.ui.mediacenter
                            .PhoneSettingsEntry(it, openMcSetup, openSettings)
                    },
                    header = { selector() },
                    send = mediaSend,
                    navigation = mediaNavigation,
                )
            },
        ) { screenModifier ->
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
                        targetSelector = selector,
                    )
                }

                tab == RemoteTab.Dock -> {
                    DockRoute(
                        dockState,
                        dockUi,
                        thumbnails,
                        { model },
                        { dock },
                        openSettings,
                        screenModifier,
                        targetSelector = selector,
                    )
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
                        targetSelector = selector,
                        dispatch = { action -> targets.dispatch(lease, action) },
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
                        targetSelector = selector,
                    )
                }
            }
        }
        if (mcSetup && targetState.selected == ControlTarget.Mediacenter) {
            MediacenterSetupDialog(onDismiss = { mcSetup = false })
        }
        if (settingsOpen) {
            ThemeSettings(theme, appearance::select, {
                settingsOpen = false
            }, themeError)
        }
        if (connectionSheet && targetState.selected == ControlTarget.LgTv) {
            TvConnectionDialog(
                state,
                { connectionSheet = false },
                { savedAddress?.let(model::connect) },
                forgetTv,
            )
        }
        if (targetState.selected == ControlTarget.LgTv) {
            error?.let { message -> ConnectionErrorDialog(message, model::clearError) }
        }
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
        mediacenter.foreground()
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
        targets.background(model::cancelInput, mediacenter::background)
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
