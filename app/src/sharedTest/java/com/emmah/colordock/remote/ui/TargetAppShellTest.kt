package com.emmah.colordock.remote.ui

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TargetAppShellTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mediacenterTextIsReadableOnDarkBackground() = checkContentColor(AppTheme.SoftDark, OrchidWhite)

    @Test fun mediacenterTextIsReadableOnLightBackground() = checkContentColor(AppTheme.WarmLight, WarmBlack)

    private fun checkContentColor(
        theme: AppTheme,
        expected: Color,
    ) {
        var observed: Color? = null
        compose.setContent {
            RemoteTheme(theme) {
                TargetAppShell(
                    ControlTargetState(selected = ControlTarget.Mediacenter),
                    RemoteTab.Remote,
                    {},
                    mediacenter = { modifier ->
                        val contentColor = LocalContentColor.current
                        SideEffect { observed = contentColor }
                        Text("Mediacenter status", modifier)
                    },
                    television = { error("Mediacenter must own the selected page") },
                )
            }
        }
        compose.runOnIdle { assertEquals(expected, observed) }
    }

    @Test fun mediacenterContentAvoidsStatusAndNavigationBars() {
        lateinit var host: View
        compose.setContent {
            host = LocalView.current
            RemoteTheme {
                TargetAppShell(
                    ControlTargetState(selected = ControlTarget.Mediacenter),
                    RemoteTab.Remote,
                    {},
                    mediacenter = { modifier -> Box(modifier.fillMaxSize().testTag("mc-body")) },
                    television = { error("Mediacenter must own the selected page") },
                )
            }
        }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(
                host,
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 80, 0, 60))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                    .build(),
            )
        }
        val body = compose.onNodeWithTag("mc-body").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Status bar must not cover Mediacenter controls: $body", body.top >= 80f)
        assertTrue("Navigation bar must not cover Mediacenter controls: $body", body.bottom <= root.bottom - 60f)
        compose.onNodeWithTag("bottom-navigation").assertDoesNotExist()
    }
}
