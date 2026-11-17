package com.emmah.colordock.remote.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.ui.mediacenter.MediaDestination
import com.emmah.colordock.remote.ui.mediacenter.MediacenterBottomNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lgLabelsAndIconsAlignAtDoubleTextSize() {
        show { RemoteAppShell(RemoteTab.Remote, {}) {} }
        checkNavigation(RemoteTab.entries.map { it.label })
    }

    @Test fun mediacenterLabelsAndIconsAlignAtDoubleTextSize() {
        show { MediacenterBottomNavigation(MediaDestination.Remote, {}) }
        checkNavigation(MediaDestination.entries.map { it.name })
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                RemoteTheme(content = content)
            }
        }
    }

    private fun checkNavigation(labels: List<String>) {
        labels.forEach { label ->
            val results = mutableListOf<TextLayoutResult>()
            compose
                .onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(label, 1, results.single().lineCount)
            val layout = results.single()
            assertFalse(label, layout.isLineEllipsized(0))
        }

        val iconTops =
            labels.map { label ->
                compose
                    .onNodeWithTag("navigation-icon-$label", useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
                    .top
            }
        assertEquals(iconTops.first(), iconTops.distinct().single())
    }
}
