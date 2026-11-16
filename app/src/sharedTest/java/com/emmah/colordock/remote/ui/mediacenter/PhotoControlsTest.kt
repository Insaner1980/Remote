package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun renderedPhotoSelectionAndIntervalStayBounded() {
        var selected: String? = null
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            RemoteTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PhotoControls(MediaSessionState(snapshot = phoneMediaSnapshot()), "complete-selection", {
                        sent.add(it)
                    }, { selection, _, _ ->
                        selected =
                            selection
                    })
                }
            }
        }
        compose.onNodeWithText("Next").performClick()
        assertEquals("photos", (sent.single() as RemoteCommand.PhotosNext).params.sessionId)
        compose.onNodeWithText("Seconds").performScrollTo().performTextReplacement("61")
        compose.onNodeWithText("Start slideshow").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Seconds").performScrollTo().performTextReplacement("60")
        compose.onNodeWithText("Start slideshow").performScrollTo().performClick()
        assertEquals("complete-selection", selected)
    }
}
