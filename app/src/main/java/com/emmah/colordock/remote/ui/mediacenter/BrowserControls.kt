package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.BrowserKey
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.ui.RemoteDimens

@Composable
internal fun BrowserControls(
    streaming: StreamingState,
    controller: BrowserInputController,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val browser = streaming.browser
    val target = streaming.target
    val notice by controller.notice.collectAsState()
    Column(modifier) {
        Text(browser?.serviceId?.let(::serviceLabel) ?: "Browser", style = MaterialTheme.typography.titleMedium)
        when {
            target == null || browser == null -> {
                Text("Controls unavailable")
            }

            enabled -> {
                BrowserActions(streaming, target, controller)
            }
        }
        if (browser?.pendingConfirmation != null) {
            Text("Approval needed")
        }
        if (notice.isNotEmpty()) Text(if (notice.contains("uncertain", true)) "Not confirmed" else "Try again")
    }
}

@Composable
private fun BrowserActions(
    streaming: StreamingState,
    target: BrowserTarget,
    controller: BrowserInputController,
    modifier: Modifier = Modifier,
) {
    val capabilities = streaming.browser?.capabilities ?: return
    val pointer = capabilities.pointer && streaming.allows(MethodName.BROWSER_POINTER)
    val keyboard = capabilities.keyboard && streaming.allows(MethodName.BROWSER_KEY)
    val text = capabilities.text && streaming.allows(MethodName.BROWSER_TEXT)
    val allowClose = streaming.allows(MethodName.BROWSER_CLOSE)
    var keyboardOpen by remember(target, keyboard, text) { mutableStateOf(false) }
    var closeOpen by remember(target, allowClose) { mutableStateOf(false) }

    Column(modifier) {
        if (pointer) {
            BrowserTouchpad(
                target,
                controller,
                canClick = streaming.allows(MethodName.BROWSER_CLICK),
                canScroll = streaming.allows(MethodName.BROWSER_SCROLL),
            )
        }
        BrowserNavigation(
            streaming,
            hasKeyboard = keyboard || text,
            actions =
                BrowserNavigationActions(
                    back = { controller.back(target) },
                    reload = { controller.reload(target) },
                    openKeyboard = {
                        controller.endGesture()
                        keyboardOpen = true
                    },
                    close =
                        if (allowClose) {
                            {
                                controller.endGesture()
                                closeOpen = true
                            }
                        } else {
                            null
                        },
                ),
        )
    }
    if (keyboardOpen) {
        BrowserKeyboard(
            keyboard,
            text,
            BrowserKeyboardActions({ controller.key(target, it) }, { controller.text(target, it) }),
            { keyboardOpen = false },
        )
    }
    if (closeOpen) {
        CloseBrowserDialog(
            confirm = {
                controller.close(target)
                closeOpen = false
            },
            dismiss = { closeOpen = false },
        )
    }
}

private data class BrowserNavigationActions(
    val back: () -> Unit,
    val reload: () -> Unit,
    val openKeyboard: () -> Unit,
    val close: (() -> Unit)?,
)

private data class BrowserAction(
    val label: String,
    val icon: Int,
    val onClick: () -> Unit,
    val description: String? = null,
)

@Composable
private fun BrowserNavigation(
    streaming: StreamingState,
    hasKeyboard: Boolean,
    actions: BrowserNavigationActions,
) {
    val capabilities = streaming.browser?.capabilities ?: return
    val canNavigate = capabilities.pointer || capabilities.keyboard || capabilities.text
    val buttons =
        buildList {
            if (canNavigate && streaming.allows(MethodName.BROWSER_BACK)) {
                add(BrowserAction("Back", R.drawable.ic_undo, actions.back, "Back"))
            }
            if (canNavigate && streaming.allows(MethodName.BROWSER_RELOAD)) {
                add(BrowserAction("Reload", R.drawable.ic_refresh, actions.reload, "Reload"))
            }
            if (hasKeyboard) add(BrowserAction("Keyboard", R.drawable.ic_keyboard, actions.openKeyboard))
            actions.close?.let { add(BrowserAction("Close tab", R.drawable.ic_close, it)) }
        }
    val columns =
        if (LocalDensity.current.fontScale > BROWSER_ACTION_STACK_FONT_SCALE) {
            BROWSER_ACTION_STACKED_COLUMNS
        } else {
            BROWSER_ACTION_COLUMNS
        }
    Column(verticalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap)) {
        if (!canNavigate) Text("Controls unavailable")
        buttons.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap)) {
                row.forEach { button ->
                    MediaKey(
                        button.label,
                        button.icon,
                        true,
                        button.onClick,
                        Modifier.weight(1f),
                        description = button.description,
                        stacked = true,
                    )
                }
            }
        }
    }
}

private const val BROWSER_ACTION_STACK_FONT_SCALE = 1.3f
private const val BROWSER_ACTION_COLUMNS = 4
private const val BROWSER_ACTION_STACKED_COLUMNS = 2

@Composable
private fun CloseBrowserDialog(
    confirm: () -> Unit,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = modifier,
        title = { Text("Close tab?") },
        confirmButton = { TextButton(onClick = confirm) { Text("Close tab") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

private data class BrowserKeyboardActions(
    val key: (BrowserKey) -> Unit,
    val text: (String) -> Boolean,
)

@Composable
private fun BrowserKeyboard(
    hasKeys: Boolean,
    hasText: Boolean,
    actions: BrowserKeyboardActions,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var composing by remember { mutableStateOf(!hasKeys && hasText) }
    if (composing) {
        TextInputSheet(
            actions.text,
            {
                composing = false
                if (!hasKeys) dismiss()
            },
            modifier,
        )
    } else {
        AlertDialog(
            onDismissRequest = dismiss,
            modifier = modifier,
            title = { Text("Keyboard") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    browserKeyRows.forEach { group ->
                        Row {
                            group.forEach { (button, label) ->
                                MediaKey(label, null, true, { actions.key(button) }, Modifier.weight(1f))
                            }
                        }
                    }
                    if (hasText) MediaKey("Type text", null, true, { composing = true })
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
        )
    }
}

private val browserKeyRows =
    listOf(
        listOf(BrowserKey.ENTER to "Enter", BrowserKey.ESCAPE to "Esc", BrowserKey.SPACE to "Space"),
        listOf(BrowserKey.TAB to "Tab", BrowserKey.SHIFT_TAB to "Shift+Tab"),
        listOf(BrowserKey.ARROW_UP to "Up", BrowserKey.ARROW_DOWN to "Down", BrowserKey.BACKSPACE to "Backspace"),
        listOf(BrowserKey.ARROW_LEFT to "Left", BrowserKey.ARROW_RIGHT to "Right"),
    )
