package com.emmah.colordock.remote.ui.mediacenter

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.RemoteDimens

@Suppress("LongParameterList")
@Composable
internal fun MediacenterDialogAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    supporting: String? = null,
    @DrawableRes trailingIcon: Int? = R.drawable.ic_keyboard_arrow_right,
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick,
        Modifier.fillMaxWidth().heightIn(min = RemoteDimens.MinTouch),
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) colors.error else colors.onSurface),
    ) {
        DockIcon(icon, if (destructive) Modifier.testTag("destructive-action-icon") else Modifier)
        Spacer(Modifier.width(RemoteDimens.BlockGap))
        Column(Modifier.weight(1f)) {
            Text(label)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        if (trailingIcon != null) DockIcon(trailingIcon)
    }
}
