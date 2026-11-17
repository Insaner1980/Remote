package com.emmah.colordock.remote.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LgDismissDialog(
    title: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = RemoteDimens.ItemGap),
                ) {
                    content()
                }
                TextButton(onDismiss, Modifier.align(Alignment.End)) { Text(dismissLabel) }
            }
        }
    }
}

@Composable
internal fun LgDialogAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick,
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) colors.error else colors.onSurface),
    ) {
        DialogActionLabel(label, icon, destructive)
        Spacer(Modifier.weight(1f))
        DockIcon(R.drawable.ic_keyboard_arrow_right, tint = colors.onSurfaceVariant)
    }
}

@Composable
internal fun DialogActionLabel(
    label: String,
    @DrawableRes icon: Int,
    destructive: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockIcon(icon, if (destructive) Modifier.testTag("destructive-action-icon") else Modifier)
        Text(label)
    }
}
