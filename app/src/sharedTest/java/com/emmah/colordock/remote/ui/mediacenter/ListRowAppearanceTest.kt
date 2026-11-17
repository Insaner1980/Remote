package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.AppTheme
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListRowAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledRowDimsItsLabelsAndKeepsItsIndependentMenuUsable() {
        var menuClicks = 0
        compose.setContent {
            RemoteTheme(AppTheme.SoftDark) {
                Column {
                    listOf("active" to true, "inactive" to false).forEach { (tag, enabled) ->
                        ListRow(
                            "Play track",
                            "Queue",
                            ListRowPosition.Single,
                            modifier = Modifier.testTag(tag),
                            leadingIcon = R.drawable.ic_play_arrow,
                            trailing = { TextButton(onClick = { menuClicks++ }) { Text("Options") } },
                            onClick = {},
                            enabled = enabled,
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("inactive").assertIsNotEnabled()
        listOf("Play track", "Queue").forEach { label ->
            assertTrue(
                "Disabled $label should be dimmer",
                labelColor("inactive", label).alpha < labelColor("active", label).alpha,
            )
        }
        assertEquals(labelColor("active", "Options"), labelColor("inactive", "Options"))
        compose
            .onNode(hasText("Options") and hasAnyAncestor(hasTestTag("inactive")))
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(1, menuClicks) }
    }

    private fun labelColor(
        row: String,
        label: String,
    ): Color {
        val layout = mutableListOf<TextLayoutResult>()
        compose
            .onNode(hasText(label) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        return layout
            .single()
            .layoutInput.style.color
    }
}
