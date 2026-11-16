package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.AppTheme
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.RemoteDimens
import com.emmah.colordock.remote.ui.RemoteTheme

internal enum class ListRowPosition { Single, First, Middle, Last }

internal enum class SelectorSize { Large, Compact }

@Composable
internal fun ListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RemoteDimens.GroupGap), content = content)
}

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun ListRow(
    title: String,
    supporting: String?,
    position: ListRowPosition,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    role: Role = Role.Button,
    verticalPadding: Dp = 12.dp,
) {
    val shape =
        when (position) {
            ListRowPosition.Single -> RoundedCornerShape(22.dp)
            ListRowPosition.First -> RoundedCornerShape(22.dp, 22.dp, 4.dp, 4.dp)
            ListRowPosition.Middle -> RoundedCornerShape(4.dp)
            ListRowPosition.Last -> RoundedCornerShape(4.dp, 4.dp, 22.dp, 22.dp)
        }
    val click =
        if (onClick ==
            null
        ) {
            Modifier
        } else {
            Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
        }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(click)
            .heightIn(min = if (supporting == null) 56.dp else RemoteDimens.RowMinHeight)
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leading != null) {
            leading()
        } else if (leadingIcon != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    DockIcon(leadingIcon, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor,
                maxLines = if (supporting == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            DockIcon(R.drawable.ic_keyboard_arrow_right, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        trailing?.invoke()
    }
}

// Explicit callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun MediaKey(
    text: String,
    icon: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Button(
        onClick,
        modifier.heightIn(min = RemoteDimens.KeyHeight).then(
            if (description == null) {
                Modifier
            } else {
                Modifier.semantics {
                    contentDescription = description
                }
            },
        ),
        enabled = enabled,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        if (icon != null) {
            DockIcon(
                icon,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f),
            )
            Spacer(Modifier.size(RemoteDimens.ItemGap))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// Explicit callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun MediaIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    FilledTonalIconButton(
        onClick,
        modifier.size(RemoteDimens.MinTouch),
        enabled = enabled,
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor =
                    if (emphasized) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                contentColor =
                    if (emphasized) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
    ) {
        DockIcon(icon, label = label)
    }
}

@Composable
internal fun DeviceSelector(
    name: String,
    connected: Boolean,
    size: SelectorSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val large = size == SelectorSize.Large
    Row(
        modifier
            .testTag("target-selector")
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Change device" }
            .height(if (large) 48.dp else 36.dp)
            .padding(start = if (large) 8.dp else 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (large) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    DockIcon(
                        R.drawable.ic_tv,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        } else {
            DockIcon(R.drawable.ic_tv, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            name,
            Modifier.weight(1f, fill = false),
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (large) {
            DockIcon(
                if (connected) R.drawable.ic_wifi else R.drawable.ic_wifi_off,
                Modifier.size(18.dp),
                label = if (connected) "Connected" else "Not connected",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DockIcon(
            R.drawable.ic_keyboard_arrow_down,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PillSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value,
        onValueChange,
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { DockIcon(R.drawable.ic_search) },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}

@Composable
internal fun PillSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value,
        onValueChange,
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { DockIcon(R.drawable.ic_search) },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}

internal fun listRowPosition(
    index: Int,
    count: Int,
): ListRowPosition =
    when {
        count == 1 -> ListRowPosition.Single
        index == 0 -> ListRowPosition.First
        index == count - 1 -> ListRowPosition.Last
        else -> ListRowPosition.Middle
    }

@Composable
internal fun EmptyListRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text,
            Modifier.heightIn(min = 56.dp).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
