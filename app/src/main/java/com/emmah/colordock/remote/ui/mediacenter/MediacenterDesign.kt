package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.AppTheme
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.RemoteDimens
import com.emmah.colordock.remote.ui.RemoteTheme

private const val DISABLED_CONTENT_ALPHA = .38f

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
    maxTitleLines: Int = if (supporting == null) 2 else 1,
) {
    val colors = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA
    val titleTint = titleColor.takeOrElse { colors.onSurface }
    val shape =
        when (position) {
            ListRowPosition.Single -> MaterialTheme.shapes.large
            ListRowPosition.First -> RoundedCornerShape(22.dp, 22.dp, 4.dp, 4.dp)
            ListRowPosition.Middle -> MaterialTheme.shapes.large.copy(CornerSize(4.dp))
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
            .background(colors.surfaceContainer)
            .then(click)
            .heightIn(min = if (supporting == null) 56.dp else RemoteDimens.RowMinHeight)
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leading != null) {
            Box(Modifier.alpha(contentAlpha)) { leading() }
        } else if (leadingIcon != null) {
            Surface(
                modifier = Modifier.alpha(contentAlpha),
                shape = CircleShape,
                color = colors.secondaryContainer,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    DockIcon(leadingIcon, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = titleTint.copy(alpha = titleTint.alpha * contentAlpha),
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            DockIcon(R.drawable.ic_keyboard_arrow_right, tint = colors.onSurfaceVariant.copy(alpha = contentAlpha))
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
    stacked: Boolean = false,
    trailingIcon: Boolean = false,
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
        contentPadding =
            if (stacked) {
                PaddingValues(horizontal = 4.dp, vertical = RemoteDimens.ItemGap)
            } else {
                ButtonDefaults.ContentPadding
            },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        if (stacked) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (icon != null) MediaKeyIcon(icon, enabled)
                Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        } else {
            if (icon != null && !trailingIcon) {
                MediaKeyIcon(icon, enabled)
                Spacer(Modifier.size(RemoteDimens.ItemGap))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
            if (icon != null && trailingIcon) {
                Spacer(Modifier.size(RemoteDimens.ItemGap))
                MediaKeyIcon(icon, enabled)
            }
        }
    }
}

@Composable
private fun MediaKeyIcon(
    icon: Int,
    enabled: Boolean,
) {
    DockIcon(
        icon,
        Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA),
    )
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
        shape = MaterialTheme.shapes.large,
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
