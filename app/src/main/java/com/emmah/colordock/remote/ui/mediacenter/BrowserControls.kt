package com.emmah.colordock.remote.ui.mediacenter

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
import com.emmah.colordock.remote.mediacenter.protocol.BrowserKey
import com.emmah.colordock.remote.mediacenter.protocol.MethodName

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
                Text("Browser controls are unavailable. Check the enrolled Chrome profile on the computer.")
            }

            enabled -> {
                BrowserActions(streaming, target, controller)
            }
        }
        if (browser?.pendingConfirmation != null) {
            Text("Browser input is paused. Review the new site before continuing.")
        }
        if (notice.isNotEmpty()) Text(notice)
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
        BrowserNavigation(streaming, target, controller) {
            controller.endGesture()
            keyboardOpen = true
        }
        if (allowClose) {
            TextButton(onClick = {
                controller.endGesture()
                closeOpen = true
            }) { Text("Close browser tab") }
        }
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

@Composable
private fun BrowserNavigation(
    streaming: StreamingState,
    target: BrowserTarget,
    controller: BrowserInputController,
    modifier: Modifier = Modifier,
    showKeyboard: () -> Unit,
) {
    val capabilities = streaming.browser?.capabilities ?: return
    val canNavigate = capabilities.pointer || capabilities.keyboard || capabilities.text
    val keyboard =
        (capabilities.keyboard && streaming.allows(MethodName.BROWSER_KEY)) ||
            (capabilities.text && streaming.allows(MethodName.BROWSER_TEXT))
    Column(modifier) {
        if (canNavigate) {
            Text("Use the website’s playback, subtitle and volume controls.")
        } else {
            Text("Browser input is temporarily unavailable.")
        }
        Row(Modifier.fillMaxWidth()) {
            if (canNavigate && streaming.allows(MethodName.BROWSER_BACK)) {
                TextButton(onClick = { controller.back(target) }) { Text("Back") }
            }
            if (canNavigate && streaming.allows(MethodName.BROWSER_RELOAD)) {
                TextButton(onClick = { controller.reload(target) }) { Text("Reload") }
            }
            if (keyboard) TextButton(onClick = showKeyboard) { Text("Keyboard") }
        }
    }
}

@Composable
private fun CloseBrowserDialog(
    confirm: () -> Unit,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = modifier,
        title = { Text("Close the enrolled browser tab?") },
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
            title = { Text("Browser keyboard") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    browserKeyRows.forEach { group ->
                        Row {
                            group.forEach { (button, label) ->
                                TextButton(onClick = { actions.key(button) }) { Text(label) }
                            }
                        }
                    }
                    if (hasText) TextButton(onClick = { composing = true }) { Text("Type on computer") }
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
