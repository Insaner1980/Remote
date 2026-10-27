package com.emmah.colordock.remote.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MainRoutesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionDialogDispatchesReconnectForgetAndCloseForBothStates() {
        var state by mutableStateOf(TvState(TvConnectionState.Ready, name = "Fixture TV"))
        var dismissed = 0
        var reconnected = 0
        var forgotten = 0
        compose.setContent {
            RemoteTheme {
                TvConnectionDialog(state, { dismissed++ }, { reconnected++ }, { forgotten++ })
            }
        }
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("Reconnect").performClick()
        compose.onNodeWithText("Forget TV").performClick()
        compose.runOnIdle { state = state.copy(connection = TvConnectionState.Failed) }
        compose.onNodeWithText("TV unavailable").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {
            assertEquals(2, dismissed)
            assertEquals(1, reconnected)
            assertEquals(1, forgotten)
        }
    }

    @Test fun connectionErrorDisplaysMessageAndCanBeDismissed() {
        var dismissed = false
        compose.setContent { RemoteTheme { ConnectionErrorDialog("Fixture failure") { dismissed = true } } }
        compose.onNodeWithText("Fixture failure").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test fun remoteRouteIgnoresStaleReadyCommandsAndOpensSettings() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val store = ViewModelStore()
        lateinit var model: RemoteViewModel
        var settings = 0
        var connections = 0
        compose.runOnIdle {
            model = RemoteViewModel(application)
            store.put("remote", model)
            model.disconnect()
        }
        try {
            compose.setContent {
                RemoteTheme {
                    RemoteRoute(
                        TvState(TvConnectionState.Ready),
                        emptyList(),
                        model,
                        TvAppsUiState(),
                        emptyMap(),
                        { connections++ },
                        { settings++ },
                    )
                }
            }
            compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Volume up").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(1, settings)
                assertNull(model.error.value)
                assertEquals(TvConnectionState.Disconnected, model.state.value.connection)
                assertEquals(0, connections)
            }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }
}
