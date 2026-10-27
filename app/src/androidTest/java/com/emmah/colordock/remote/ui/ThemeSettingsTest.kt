package com.emmah.colordock.remote.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.emmah.colordock.remote.MainActivity
import org.junit.Rule
import org.junit.Test

class ThemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun settings() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
        compose.onNodeWithText("App theme").assertIsDisplayed()
    }

    private fun choose(theme: AppTheme) {
        compose.onNodeWithText(theme.label).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(theme.label) and isSelected()).fetchSemanticsNodes().size == 1
        }
    }

    @Test fun bothChoicesPersistAcrossActivityRecreation() {
        settings()
        for (theme in AppTheme.entries) {
            choose(theme)
            compose.onNodeWithText("Done").performClick()
            compose.activityRule.scenario.recreate()
            settings()
            compose.onNodeWithText(theme.label).performScrollTo().assertIsSelected()
        }
        compose.onNodeWithText("Done").performClick()
    }

    @Test fun settingsWorkOfflineFromEveryTabAndPhoneBackDismissesThem() {
        settings()
        choose(AppTheme.WarmLight)
        Espresso.pressBack()
        compose.onNodeWithText("App theme").assertDoesNotExist()
        for (tab in listOf("Wallpapers", "Color Dock", "Remote")) {
            compose.onNodeWithText(tab, useUnmergedTree = true).performClick()
            settings()
            compose.onNodeWithText("Warm light").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Done").performClick()
        }
    }
}
