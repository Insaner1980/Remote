package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediacenterSetupScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addMediacenterIsReachableWithoutATelevisionConnection() {
        var opened = 0
        compose.setContent { RemoteTheme { MediacenterEntry({ opened++ }) } }
        compose.waitForIdle()
        compose.onNodeWithText("Add Mediacenter").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun cameraDenialKeepsManualPairingReachable() {
        var selected = ""
        var scans = 0
        compose.setContent {
            RemoteTheme {
                MediacenterSetupScreen(
                    true,
                    true,
                    { if (it == MediacenterSetupPermission.Camera) scans++ },
                    { address, port -> selected = "$address:$port" },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Camera access declined. You can pair manually.").assertIsDisplayed()
        compose.onNodeWithText("Computer address").performScrollTo().performTextInput("192.168.1.9")
        compose.waitForIdle()
        compose.onNodeWithText("Compare verification phrase").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("192.168.1.9:47831", selected)
            assertEquals(0, scans)
        }
    }

    @Test fun lanDenialHasItsOwnPermissionActionAndKeepsManualFields() {
        var lanRequests = 0
        var cameraRequests = 0
        compose.setContent {
            RemoteTheme {
                MediacenterSetupScreen(false, false, { permission ->
                    if (permission == MediacenterSetupPermission.LocalNetwork) lanRequests++ else cameraRequests++
                }, { _, _ -> })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Allow local network access").performClick()
        compose.onNodeWithText("Scan code").assertIsNotEnabled()
        compose.onNodeWithText("Computer address").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, lanRequests)
            assertEquals(0, cameraRequests)
        }
    }
}
