package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.assertDestructiveIcon
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediacenterDestructiveActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun removeComputerRowHasAnIcon() {
        compose.setContent {
            RemoteTheme {
                MediacenterDialogAction("Remove computer", R.drawable.ic_delete, {}, destructive = true)
            }
        }
        compose.assertDestructiveIcon("Remove computer")
    }

    @Test fun eachDestructiveConfirmationHasAnIcon() {
        val actions =
            listOf(ActionKind.REMOVE_SOURCE, ActionKind.REVOKE_DEVICE, ActionKind.EJECT_DRIVE, ActionKind.END_SESSION)
        val action = mutableStateOf(actions.first())
        compose.setContent {
            RemoteTheme {
                ConfirmationSheet(
                    ConfirmationPreview(
                        "fixture-confirmation",
                        "fixture-phone",
                        action.value,
                        listOf("Example"),
                        Expected(),
                        30_000,
                        confirmMethod = ConfirmMethod.ACTIVITY_CONFIRM,
                    ),
                    false,
                    ConfirmationActions({}, {}),
                )
            }
        }
        actions.forEach {
            compose.runOnIdle { action.value = it }
            compose.assertDestructiveIcon("Confirm")
        }
    }
}
