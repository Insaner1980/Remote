package com.emmah.colordock.remote.ui

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText

internal fun SemanticsNodeInteractionsProvider.assertDestructiveIcon(label: String) {
    onNode(
        hasClickAction() and hasAnyDescendant(hasText(label)) and
            hasAnyDescendant(hasTestTag("destructive-action-icon")),
        useUnmergedTree = true,
    ).assertExists()
}
