package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.BrowserKey
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.ui.DockIcon
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
                BrowserKeyboardGrid(hasText, actions.key) { composing = true }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
        )
    }
}

@Composable
private fun BrowserKeyboardGrid(
    hasText: Boolean,
    onKey: (BrowserKey) -> Unit,
    onText: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        val gap = RemoteDimens.ItemGap
        val cellWidth = ((maxWidth - gap * (BROWSER_KEY_COLUMNS - 1)) / BROWSER_KEY_COLUMNS).coerceAtLeast(0.dp)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            browserKeyRows.forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    keys.forEach { key ->
                        if (key == null) {
                            Spacer(Modifier.width(cellWidth))
                        } else {
                            val width = cellWidth * key.span + gap * (key.span - 1)
                            BrowserKeyButton(key, { onKey(key.key) }, Modifier.width(width))
                        }
                    }
                }
            }
            if (hasText) {
                OutlinedButton(onText, Modifier.fillMaxWidth().heightIn(min = RemoteDimens.MinTouch)) {
                    DockIcon(R.drawable.ic_keyboard)
                    Spacer(Modifier.width(gap))
                    Text("Type text")
                }
            }
        }
    }
}

@Composable
private fun BrowserKeyButton(
    key: BrowserKeyCell,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick,
        modifier.height(RemoteDimens.KeyHeight),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurface,
            ),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        if (key.icon != null) {
            DockIcon(key.icon, label = key.label)
        } else {
            val style = MaterialTheme.typography.labelLarge
            BasicText(
                key.label,
                style = style.copy(color = LocalContentColor.current),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
            )
        }
    }
}

private data class BrowserKeyCell(
    val key: BrowserKey,
    val label: String,
    val icon: Int? = null,
    val span: Int = 1,
)

private const val BROWSER_KEY_COLUMNS = 3

private val browserKeyRows =
    listOf(
        listOf(
            BrowserKeyCell(BrowserKey.ESCAPE, "Esc"),
            BrowserKeyCell(BrowserKey.TAB, "Tab"),
            BrowserKeyCell(BrowserKey.SHIFT_TAB, "Shift+Tab"),
        ),
        listOf(null, BrowserKeyCell(BrowserKey.ARROW_UP, "Up", R.drawable.ic_keyboard_arrow_up), null),
        listOf(
            BrowserKeyCell(BrowserKey.ARROW_LEFT, "Left", R.drawable.ic_keyboard_arrow_left),
            BrowserKeyCell(BrowserKey.ARROW_DOWN, "Down", R.drawable.ic_keyboard_arrow_down),
            BrowserKeyCell(BrowserKey.ARROW_RIGHT, "Right", R.drawable.ic_keyboard_arrow_right),
        ),
        listOf(
            BrowserKeyCell(BrowserKey.SPACE, "Space", span = 2),
            BrowserKeyCell(BrowserKey.BACKSPACE, "Backspace", R.drawable.ic_backspace),
        ),
        listOf(BrowserKeyCell(BrowserKey.ENTER, "Enter", span = BROWSER_KEY_COLUMNS)),
    )
