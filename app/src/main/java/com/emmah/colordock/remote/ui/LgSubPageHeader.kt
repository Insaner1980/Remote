package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.R

@Composable
internal fun LgSubPageHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
    ) {
        IconButton(onBack, Modifier.size(RemoteDimens.MinTouch), enabled = backEnabled) {
            DockIcon(R.drawable.ic_arrow_back, label = "Back")
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
    }
}
