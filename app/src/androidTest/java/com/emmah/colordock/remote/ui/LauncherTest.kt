package com.emmah.colordock.remote.ui

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.emmah.colordock.remote.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LauncherTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openSettings() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
        compose.onNodeWithText("App theme").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
    }

    @Test fun launcherBrandingAndRecreationReachUsableApp() {
        val activity = compose.activity
        val manager = activity.packageManager
        val info = activity.applicationInfo
        assertEquals("Remote", manager.getApplicationLabel(info).toString())
        assertTrue(manager.getApplicationIcon(info) is AdaptiveIconDrawable)
        assertEquals(
            MainActivity::class.java.name,
            manager.getLaunchIntentForPackage(activity.packageName)?.component?.className,
        )
        openSettings()
        compose.activityRule.scenario.recreate()
        openSettings()
    }
}
