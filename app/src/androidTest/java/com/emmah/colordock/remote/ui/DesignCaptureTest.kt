package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockSnapshot
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.companion.WallpaperMetadata
import com.emmah.colordock.remote.companion.WallpaperSettings
import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class DesignCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val ids =
        listOf(
            "layered-magenta-and-turquoise-paper-waves",
            "emerald-mist-abstract-wallpaper",
            "misty-blue-and-white-abstract-flow",
            "vibrant-woven-yarn-wave",
            "retro-floppy-disk-trio",
            "layered-crimson-paper-waves",
        )
    private val pictures =
        ids.mapIndexed {
            index,
            id,
            ->
            WallpaperMetadata(
                "user:00000000-0000-4000-8000-${index.toString().padStart(12,'0')}",
                id.replace('-', ' '),
                "cover",
                null,
                null,
            )
        }
    private val snapshot =
        DockSnapshot(
            7,
            WallpaperSettings(
                pictures[0].id,
                listOf(pictures[0].id, pictures[2].id, pictures[4].id),
                "shuffle",
                "open",
            ),
            pictures[0],
            6,
            139,
            0,
            false,
            null,
            shelfApps.map {

                it.first
            },
            emptyList(),
            false,
        )

    private fun capture(
        tab: RemoteTab,
        theme: AppTheme,
        width: Int? = null,
        scale: Float? = null,
    ) {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        // Match the reference's 100% type scale without changing the user's device settings.
        val fontScale = scale ?: InstrumentationRegistry.getArguments().getString("designFontScale")?.toFloat() ?: 1f
        var pixelsPerDp = 0f
        val images =
            ids
                .mapIndexed {
                    index,
                    id,
                    ->
                    val file = File(context.cacheDir, "design-$id.webp")
                    instrument.context.assets.open("design/$id.webp").use { input ->
                        file.outputStream().use {
                            input.copyTo(it)
                        }
                    }
                    pictures[index].id to
                        file
                }.toMap()
        compose.setContent {
            val density =
                if (width ==
                    null
                ) {
                    LocalDensity.current.density
                } else {
                    LocalWindowInfo.current.containerSize.width / width.toFloat()
                }
            pixelsPerDp = density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                RemoteTheme(theme) {
                    CaptureContent(tab, width, images)
                }
            }
        }
        compose.waitForIdle()
        waitForImages(tab)
        saveCapture(tab, theme, width, scale, fontScale)
        images.values.forEach {
            it.delete()
        }
        assertNavigationClearance(tab, pixelsPerDp, fontScale)
    }

    private fun saveCapture(
        tab: RemoteTab,
        theme: AppTheme,
        width: Int?,
        scale: Float?,
        fontScale: Float,
    ) {
        val directory =
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-captures").apply {
                mkdirs()
            }
        val suffix = if (width != null || scale != null) "-${width ?: 411}dp-$fontScale" else ""
        if (tab == RemoteTab.Remote) {
            if (fontScale > 1f ||
                width == 320
            ) {
                File(directory, "${theme.name.lowercase()}-remote$suffix-top.png").outputStream().use { output ->
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(
                        android.graphics.Bitmap.CompressFormat.PNG,
                        100,
                        output,
                    )
                }
            }
            compose.onNodeWithTag("app-shortcuts").performScrollTo()
            compose
                .onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("direction-pad")))
                .performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, 100_000f)
                }
        }
        val file = File(directory, "${theme.name.lowercase()}-${tab.name.lowercase()}$suffix.png")
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val background = if (theme == AppTheme.WarmLight) OrchidWhite else WarmBlack
            assertEquals(
                "Android must not automatically recolor the selected app theme",
                background.toArgb(),
                bitmap.getPixel(
                    1,
                    bitmap.height / 2,
                ),
            )
        }
    }

    private fun waitForImages(tab: RemoteTab) {
        // Let the file-backed image decode complete before taking the visual evidence.
        if (tab !=
            RemoteTab.Remote
        ) {
            compose.waitUntil(5000) {
                compose
                    .onAllNodes(
                        hasTestTag("wallpaper-image") and
                            (
                                hasContentDescription(pictures[0].title) or
                                    hasContentDescription("Current wallpaper clock preview")
                            ),
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    private fun assertNavigationClearance(
        tab: RemoteTab,
        pixelsPerDp: Float,
        fontScale: Float,
    ) {
        if (tab == RemoteTab.Remote) {
            val navigationTop =
                compose
                    .onNodeWithTag("bottom-navigation")
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            compose.onNodeWithTag("app-shortcuts").assertIsDisplayed()
            val bottom =
                compose
                    .onNodeWithTag("app-shortcuts")
                    .fetchSemanticsNode()
                    .boundsInRoot.bottom
            val expected = (24 * pixelsPerDp).roundToInt()
            assertTrue(
                "App shortcuts gap=${navigationTop - bottom}px must leave ${expected}px (24 dp) " +
                    "above navigation at fontScale=$fontScale",
                navigationTop - bottom >= expected,
            )
        } else {
            val tag = if (tab == RemoteTab.Wallpapers) "shuffle-settings" else "app-shelf-entry"
            // Bring the final section into view before checking navigation overlap.
            if (tab == RemoteTab.Wallpapers) {
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
            }
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("Complete $tag must be visible after scrolling", bounds.top >= 0.dp)
            val bottom = bounds.bottom
            val navigationTop = compose.onNodeWithTag("bottom-navigation").getUnclippedBoundsInRoot().top
            assertTrue(
                "Complete $tag bottom=$bottom must fit above navigation=$navigationTop at fontScale=$fontScale",
                bottom <= navigationTop,
            )
        }
    }

    @Composable
    private fun CaptureContent(
        tab: RemoteTab,
        width: Int?,
        images: Map<String, File>,
    ) {
        RemoteAppShell(tab, {}) { modifier ->
            when (tab) {
                RemoteTab.Remote -> {
                    CaptureRemote(width, modifier)
                }

                RemoteTab.Wallpapers -> {
                    WallpaperTestScreen(
                        DockState(DockConnectionState.Ready, snapshot),
                        WallpaperUiState(items = pictures),
                        modifier = modifier,
                        thumbnails = images,
                    )
                }

                RemoteTab.Dock -> {
                    DockScreen(
                        DockState(
                            DockConnectionState.Ready,
                            snapshot,
                        ),
                        DockUiState(),
                        images[pictures[0].id].asImageFile(),
                        {},
                        {},
                        {},
                        {},
                        { _, _, _ ->
                        },
                        {},
                        {},
                        {},
                        modifier,
                    )
                }
            }
        }
    }

    @Composable
    private fun CaptureRemote(
        width: Int?,
        modifier: Modifier = Modifier,
    ) {
        Box(
            if (width ==
                null
            ) {
                modifier
            } else {
                modifier.width(width.dp)
            },
        ) {
            RemoteScreen(
                TvState(
                    TvConnectionState.Ready,
                    name = "Living room TV",
                    muted = false,
                    guideAvailable = true,
                ),
                emptyList(),
                {},
                {},
                {},
                {},
                {},
                {},
                apps =
                    TvAppsUiState(
                        apps =
                            listOf(
                                TvApp("netflix", "Netflix", null),
                                TvApp("youtube.leanback.v4", "YouTube", null),
                                TvApp("fi.yle.areena", "Yle Areena", null),
                                TvApp("mtvkatsomo", "MTV Katsomo", null),
                                TvApp(
                                    "unknown",
                                    "Independent television and films from around the world",
                                    null,
                                ),
                            ),
                        hasLoaded = true,
                        connected = true,
                    ),
            )
        }
    }

    @Test fun remoteLight() = capture(RemoteTab.Remote, AppTheme.WarmLight)

    @Test fun remoteDark() = capture(RemoteTab.Remote, AppTheme.SoftDark)

    @Test fun remoteAt115PercentLight() = capture(RemoteTab.Remote, AppTheme.WarmLight, scale = 1.15f)

    @Test fun remoteAt115PercentDark() = capture(RemoteTab.Remote, AppTheme.SoftDark, scale = 1.15f)

    @Test fun remote400Light() = capture(RemoteTab.Remote, AppTheme.WarmLight, 400, 1f)

    @Test fun remote400Dark() = capture(RemoteTab.Remote, AppTheme.SoftDark, 400, 1f)

    @Test fun remote320Light() = capture(RemoteTab.Remote, AppTheme.WarmLight, 320, 1f)

    @Test fun remote320Dark() = capture(RemoteTab.Remote, AppTheme.SoftDark, 320, 1f)

    @Test fun remoteLargeLight() = capture(RemoteTab.Remote, AppTheme.WarmLight, 400, 2f)

    @Test fun remoteLargeDark() = capture(RemoteTab.Remote, AppTheme.SoftDark, 400, 2f)

    @Test fun remote320LargeLight() = capture(RemoteTab.Remote, AppTheme.WarmLight, 320, 2f)

    @Test fun remote320LargeDark() = capture(RemoteTab.Remote, AppTheme.SoftDark, 320, 2f)

    @Test fun wallpapersLight() = capture(RemoteTab.Wallpapers, AppTheme.WarmLight)

    @Test fun wallpapersDark() = capture(RemoteTab.Wallpapers, AppTheme.SoftDark)

    @Test fun dockLight() = capture(RemoteTab.Dock, AppTheme.WarmLight)

    @Test fun dockDark() = capture(RemoteTab.Dock, AppTheme.SoftDark)
}
