package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeContractsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adaptivePairPreservesStateAndUpdatesContentAcrossLayouts() {
        var narrow by mutableStateOf(false)
        var largeText by mutableStateOf(false)
        var label by mutableStateOf("Initial")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else 1f)) {
                RemoteTheme {
                    Box(Modifier.width(if (narrow) 280.dp else 320.dp)) {
                        AdaptivePair(
                            first = { CounterButton("$label first", it) },
                            second = { CounterButton("$label second", it) },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Initial first: 0").performClick()
        compose.onNodeWithText("Initial second: 0").performClick()
        compose.runOnIdle {
            narrow = true
            label = "Updated"
        }
        compose.onNodeWithText("Updated first: 1").assertExists()
        compose.onNodeWithText("Updated second: 1").assertExists()
        compose.runOnIdle { narrow = false }
        compose.onNodeWithText("Updated first: 1").assertExists()
        compose.runOnIdle { largeText = true }
        compose.onNodeWithText("Updated first: 1").assertExists()
        compose.onNodeWithText("Updated second: 1").assertExists()
        compose.runOnIdle { largeText = false }
        compose.onNodeWithText("Updated first: 1").assertExists()
        compose.onNodeWithText("Updated second: 1").assertExists()
    }

    @Test fun remoteDisposalUsesLatestReleaseWithoutReleasingOnRecomposition() {
        var visible by mutableStateOf(true)
        var oldReleases = 0
        var newReleases = 0
        var onRelease by mutableStateOf<() -> Unit>({ oldReleases++ })
        compose.setContent {
            RemoteTheme {
                if (visible) {
                    RemoteScreen(TvState(TvConnectionState.Ready), emptyList(), {}, {}, onRelease, {}, {}, {})
                }
            }
        }
        compose.runOnIdle { onRelease = { newReleases++ } }
        compose.runOnIdle {
            oldReleases = 0
            newReleases = 0
            onRelease = { newReleases++ }
        }
        compose.runOnIdle {
            assertEquals(0, oldReleases)
            assertEquals(0, newReleases)
            visible = false
        }
        compose.runOnIdle {
            assertEquals(0, oldReleases)
            assertEquals(1, newReleases)
        }
    }
}

@Composable
private fun CounterButton(
    label: String,
    modifier: Modifier = Modifier,
) {
    var count by remember { mutableIntStateOf(0) }
    TextButton({ count++ }, modifier) { Text("$label: $count") }
}
