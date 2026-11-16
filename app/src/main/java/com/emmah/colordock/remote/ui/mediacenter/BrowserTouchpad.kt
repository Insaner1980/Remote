package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.protocol.PointerButton

private const val TWO_FINGERS = 2

internal data class BrowserTouchActions(
    val point: (BrowserPosition) -> Unit,
    val click: (BrowserPosition, PointerButton) -> Unit,
    val scroll: (Double, Double) -> Unit,
    val release: () -> Unit,
)

@Composable
internal fun BrowserTouchpad(
    target: BrowserTarget,
    controller: BrowserInputController,
    modifier: Modifier = Modifier,
    canClick: Boolean = true,
    canScroll: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    val directions =
        listOfNotNull(
            "Slide to move the pointer",
            "tap to click".takeIf { canClick },
            "swipe with two fingers to scroll".takeIf { canScroll },
        ).joinToString("\n")
    Box(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag("browser-touchpad")
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .pointerInput(target, controller, canClick, canScroll) {
                val touch =
                    BrowserTouchActions(
                        { controller.point(target, it) },
                        { position, button -> if (canClick) controller.click(target, position, button) },
                        { x, y -> if (canScroll) controller.scroll(target, x, y) },
                        controller::endGesture,
                    )
                awaitEachGesture { trackBrowserGesture(touch) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(directions, Modifier.padding(20.dp))
    }
    DisposableEffect(target, controller) { onDispose { controller.endGesture() } }
}

private suspend fun AwaitPointerEventScope.trackBrowserGesture(touch: BrowserTouchActions) {
    val down = awaitFirstDown(pass = PointerEventPass.Initial)
    val start = down.position
    val limit = viewConfiguration.touchSlop
    var canTap = true
    var multiTouch = false
    down.consume()
    normalizedBrowserPosition(start.x, start.y, size.width, size.height)?.let(touch.point)
    try {
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.isConsumed }) return
            val active = event.changes.count { it.pressed }
            multiTouch = multiTouch || active >= TWO_FINGERS
            if (multiTouch && active >= TWO_FINGERS) {
                val delta = event.calculatePan()
                touch.scroll(-delta.x.toDouble(), -delta.y.toDouble())
            }
            val primary = event.changes.firstOrNull { it.id == down.id && !multiTouch }
            if (primary != null) {
                canTap = canTap && (primary.position - start).getDistance() < limit
                dispatchSingleTouch(primary, canTap, touch)
            }
            event.changes.forEach { it.consume() }
        } while (active > 0)
    } finally {
        touch.release()
    }
}

private fun AwaitPointerEventScope.dispatchSingleTouch(
    pointer: PointerInputChange,
    tap: Boolean,
    touch: BrowserTouchActions,
) {
    val position =
        normalizedBrowserPosition(pointer.position.x, pointer.position.y, size.width, size.height)
            ?: return
    if (pointer.changedToUpIgnoreConsumed() && tap) {
        touch.click(position, PointerButton.LEFT)
    } else if (pointer.pressed) {
        touch.point(position)
    }
}
