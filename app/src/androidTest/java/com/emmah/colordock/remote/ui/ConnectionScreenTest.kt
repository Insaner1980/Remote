package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvErrorCode
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionAtDefaultWidth() = checkReachability(400, 1f)

    @Test fun connectionAt320dp() = checkReachability(320, 1f)

    @Test fun connectionAt200PercentFont() = checkReachability(400, 2f)

    @Test fun connectionAt320dpAnd200PercentFont() = checkReachability(320, 2f)

    @Composable
    private fun ConnectionTestFrame(
        width: Int,
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        val actual = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(actual.density, fontScale)) {
            RemoteTheme(
                AppTheme.fromStored(
                    androidx.test.platform.app.InstrumentationRegistry
                        .getArguments()
                        .getString("theme"),
                ),
            ) {
                Surface {
                    Box(Modifier.width(width.dp)) {
                        content()
                    }
                }
            }
        }
    }

    private fun checkReachability(
        width: Int,
        fontScale: Float,
    ) {
        var selected: String? = null
        compose.setContent {
            ConnectionTestFrame(width, fontScale) {
                ConnectionScreen(
                    state = TvState(),
                    needsSettings = false,
                    onConnect = {
                        selected = it
                    },
                    onCancel = {},
                    onOpenSettings = {},
                )
            }
        }
        compose.onNodeWithText("Connect TV").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Forget TV").assertDoesNotExist()
        compose.onNodeWithText("TV IP address").performScrollTo().performTextInput("192.168.1.102")
        compose
            .onNodeWithText("Connect TV")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals("192.168.1.102", selected)
        }
    }

    @Test fun deniedPermissionOffersReachableSettingsAt320dpAnd200PercentFont() {
        var opened = false
        compose.setContent {
            ConnectionTestFrame(320, 2f) {
                ConnectionScreen(
                    state = TvState(TvConnectionState.PermissionRequired),
                    needsSettings = true,
                    onConnect = {},
                    onCancel = {},
                    onOpenSettings = {
                        opened = true
                    },
                )
            }
        }
        compose
            .onNodeWithText("Open settings")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(true, opened)
        }
    }

    @Test fun savedTvRecoveryAtDefaultWidth() = checkRecovery(400, 1f)

    @Test fun savedTvRecoveryAt320dp() = checkRecovery(320, 1f)

    @Test fun savedTvRecoveryAt200PercentFont() = checkRecovery(400, 2f)

    @Test fun savedTvRecoveryAt320dpAnd200PercentFont() = checkRecovery(320, 2f)

    private fun checkRecovery(
        width: Int,
        fontScale: Float,
    ) {
        val failures =
            listOf(
                TvState(TvConnectionState.Failed, error = TvErrorCode.PairingRequired),
                TvState(TvConnectionState.Failed, error = TvErrorCode.IdentityChanged),
                TvState(TvConnectionState.Failed, error = TvErrorCode.StorageUnavailable),
                TvState(TvConnectionState.PermissionRequired),
            )
        val state = mutableStateOf(failures.first())
        var forgotten = 0
        var connected = 0
        compose.setContent {
            ConnectionTestFrame(width, fontScale) {
                ConnectionScreen(
                    state = state.value,
                    needsSettings = false,
                    onConnect = {
                        connected++
                    },
                    onCancel = {},
                    onOpenSettings = {},
                    initialAddress = "192.168.1.102",
                    onForget = {
                        forgotten++
                    },
                )
            }
        }
        failures.forEachIndexed { index, failure ->
            compose.runOnIdle {
                state.value = failure
                assertEquals(index, forgotten)
            }
            compose
                .onNodeWithText("Forget TV")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle {
                assertEquals(index + 1, forgotten)
                assertEquals(0, connected)
            }
        }
        compose.runOnIdle {
            state.value = TvState(TvConnectionState.Pairing)
        }
        compose.onNodeWithText("Forget TV").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(failures.size, forgotten)
        }
    }
}
