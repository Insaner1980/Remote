package com.emmah.colordock.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockSnapshot
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.companion.WeatherLocation
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val shelfApps =
    listOf(
        "youtube.leanback.v4" to "YouTube",
        "netflix" to "Netflix",
        "com.wbd.stream" to "HBO Max",
        "viaplay-lg" to "Viaplay",
        "fi.yle.areena" to "Yle Areena",
        "mtvkatsomo" to "MTV Katsomo",
        "fi.nelonenmedia.ruutu" to "Ruutu",
        "cdp-30" to "Plex",
    )

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
fun DockScreen(
    state: DockState,
    ui: DockUiState,
    thumbnail: ImageFile?,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onClock: () -> Unit,
    onPatch: (DockPatch) -> Unit,
    onShelf: (List<String>, List<String>, Long) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onLocation: (WeatherLocation) -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
    targetSelector: (@Composable () -> Unit)? = null,
) {
    var page by rememberSaveable { mutableStateOf("home") }
    val snapshot = state.snapshot
    BackHandler(page != "home") {
        page = "home"
    }
    if (page == "shelf" && snapshot != null) {
        AppShelfScreen(snapshot, ui.saving, ui.error, onSave = onShelf, onCancel = {
            page = "home"
        }, modifier)
        return
    }
    if (page == "location") {
        DockLocationScreen(ui, onQuery, onSearch, onLocation, { page = "home" }, modifier)
        return
    }
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now()
            delay(CLOCK_REFRESH_MS)
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = RemoteDimens.ItemGap, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RemoteHeader(
            onSettings,
            selector =
                targetSelector ?: {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Color Dock", style = MaterialTheme.typography.headlineSmall)
                        Text("Home screen settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
        )
        if (state.connection != DockConnectionState.Ready || snapshot == null) {
            DockUnavailable(state, onOpen, onJoin)
            return@Column
        }
        DockClockSettings(
            snapshot,
            ui.saving,
            thumbnail,
            clock.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)),
            onClock,
            onPatch,
        )
        DockWeatherSettings(snapshot, ui.saving, onPatch) { page = "location" }
        DockAppSettings(ui) { page = "shelf" }
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun DockLocationScreen(
    ui: DockUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onLocation: (WeatherLocation) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RemoteDimens.ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LgSubPageHeader("Choose location", onBack)
        OutlinedTextField(
            ui.query,
            {
                onQuery(it.take(MAX_LOCATION_QUERY_LENGTH))
            },
            Modifier.fillMaxWidth(),
            label = {
                Text("City or place")
            },
            singleLine = true,
        )
        Button(
            onSearch,
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled =
                ui.query.trim().length in 2..MAX_LOCATION_QUERY_LENGTH && !ui.searching,
        ) {
            Text("Search")
        }
        if (ui.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (ui.searched && ui.results.isEmpty()) Text("No locations found. Try another city or place name.")
        ui.results.forEach { location ->
            OutlinedCard(onClick = {
                onLocation(location)
                onBack()
            }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(location.label, Modifier.padding(RemoteDimens.ScreenGutter))
            }
        }
        ui.error?.let {
            Text(
                it,
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "Weather data: Open-Meteo",
            Modifier.padding(top = RemoteDimens.ItemGap),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun ColumnScope.DockClockSettings(
    snapshot: DockSnapshot,
    saving: Boolean,
    thumbnail: ImageFile?,
    clock: String,
    onClock: () -> Unit,
    onPatch: (DockPatch) -> Unit,
) {
    DockSectionTitle("Clock mode")
    Box(Modifier.fillMaxWidth().aspectRatio(CLOCK_PREVIEW_ASPECT_RATIO).clip(RemoteShapes.Inner)) {
        Picture(
            thumbnail,
            "Current wallpaper clock preview",
            fit = snapshot.current.fit,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            Modifier.align(Alignment.Center),
            MaterialTheme.shapes.medium,
            WarmBlack.copy(alpha = CLOCK_OVERLAY_ALPHA),
            contentColor = OrchidWhite,
        ) {
            Text(
                clock,
                Modifier.padding(8.dp),
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
    Button(
        onClock,
        Modifier.fillMaxWidth().heightIn(min = RemoteDimens.KeyHeight),
        enabled = !saving,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        DockIcon(if (snapshot.clockActive) R.drawable.ic_close else R.drawable.ic_play_arrow)
        Spacer(Modifier.width(10.dp))
        Text(if (snapshot.clockActive) "Stop clock mode" else "Start clock mode")
    }
    ChoiceRow(
        "Start automatically",
        listOf("0" to "Off", "5" to "After 5 minutes", "10" to "After 10 minutes", "30" to "After 30 minutes"),
        snapshot.idleMinutes.toString(),
        !saving,
    ) {
        onPatch(DockPatch.Clock(it.toInt()))
    }
    Text(
        "Press OK or Back on the TV remote to leave clock mode.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DockWeatherSettings(
    snapshot: DockSnapshot,
    saving: Boolean,
    onPatch: (DockPatch) -> Unit,
    onLocation: () -> Unit,
) {
    DockSectionTitle("Weather")
    OutlinedCard(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show weather", Modifier.weight(1f))
                Switch(
                    snapshot.weatherEnabled,
                    { enabled ->
                        if (enabled &&
                            snapshot.location == null
                        ) {
                            onLocation()
                        } else {
                            onPatch(DockPatch.Weather(enabled, snapshot.location))
                        }
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Show weather"
                        },
                    enabled = !saving,
                )
            }
            HorizontalDivider()
            TextButton(
                {
                    onLocation()
                },
                Modifier.fillMaxWidth().heightIn(
                    min = 60.dp,
                ),
                enabled = !saving,
                shape =
                RectangleShape,
                contentPadding =
                    PaddingValues(
                        vertical = 8.dp,
                    ),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Location")
                    Text(
                        snapshot.location?.label ?: "Choose location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DockIcon(R.drawable.ic_keyboard_arrow_right)
            }
        }
    }
}

private const val CLOCK_REFRESH_MS = 1000L
private const val MAX_LOCATION_QUERY_LENGTH = 120
private const val CLOCK_PREVIEW_ASPECT_RATIO = 2.2f
private const val CLOCK_OVERLAY_ALPHA = .65f

@Composable
private fun ColumnScope.DockAppSettings(
    ui: DockUiState,
    onShelf: () -> Unit,
) {
    DockSectionTitle("Apps")
    OutlinedCard(onClick = {
        onShelf()
    }, modifier = Modifier.fillMaxWidth().testTag("app-shelf-entry"), shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Edit app shelf", Modifier.weight(1f))
            DockIcon(R.drawable.ic_keyboard_arrow_right)
        }
    }
    if (ui.saving) Text("Saving\u2026")
    ui.error?.let {
        Text(
            it,
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DockSectionTitle(title: String) {
    Text(
        title,
        Modifier.padding(top = RemoteDimens.SectionGap - RemoteDimens.ItemGap),
        style = MaterialTheme.typography.titleMedium,
    )
}
