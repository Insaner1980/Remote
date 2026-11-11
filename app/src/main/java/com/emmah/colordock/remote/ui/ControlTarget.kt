package com.emmah.colordock.remote.ui

/** Commands retain their target and selection generation through asynchronous callbacks. */
enum class ControlTarget(
    val label: String,
) {
    LgTv("LG TV"),
    Mediacenter("Mediacenter"),
}

internal data class ControlTargetLease(
    val target: ControlTarget,
    val generation: Long,
)

internal data class ControlTargetState(
    val selected: ControlTarget = ControlTarget.LgTv,
    val generation: Long = 0,
    val tvReady: Boolean = false,
    val mediacenterReady: Boolean = false,
) {
    fun ready(target: ControlTarget): Boolean =
        when (target) {
            ControlTarget.LgTv -> tvReady
            ControlTarget.Mediacenter -> mediacenterReady
        }
}
