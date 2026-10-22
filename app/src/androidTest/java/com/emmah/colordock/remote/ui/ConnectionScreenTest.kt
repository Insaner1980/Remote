package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ConnectionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionAtDefaultWidth() = checkReachability(400, 1f)
    @Test fun connectionAt320dp() = checkReachability(320, 1f)
    @Test fun connectionAt200PercentFont() = checkReachability(400, 2f)
    @Test fun connectionAt320dpAnd200PercentFont() = checkReachability(320, 2f)

    private fun checkReachability(width: Int, fontScale: Float) {
        var selected: String? = null
        compose.setContent {
            val actual = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(actual.density, fontScale)) {
                RemoteTheme(AppTheme.fromStored(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("theme"))) {
                    Surface {
                        Box(Modifier.width(width.dp)) {
                            ConnectionScreen(
                                state = TvState(), needsSettings = false,
                                onConnect = { selected = it }, onCancel = {}, onOpenSettings = {},
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Connect TV").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("TV IP address").performScrollTo().performTextInput("192.168.1.102")
        compose.onNodeWithText("Connect TV").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("192.168.1.102", selected) }
    }

    @Test fun deniedPermissionOffersReachableSettingsAt320dpAnd200PercentFont() {
        var opened = false
        compose.setContent {
            val actual = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(actual.density, 2f)) {
                RemoteTheme(AppTheme.fromStored(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("theme"))) {
                    Surface {
                        Box(Modifier.width(320.dp)) {
                            ConnectionScreen(
                                state = TvState(TvConnectionState.PermissionRequired), needsSettings = true,
                                onConnect = {}, onCancel = {}, onOpenSettings = { opened = true },
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Open settings").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }
}
