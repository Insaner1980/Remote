package com.emmah.colordock.remote.ui.mediacenter

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.ui.DockIcon

@Composable
internal fun LibraryItemRow(
    item: MediaItem,
    connected: Boolean,
    actions: LibraryActions,
    thumbnail: suspend (String) -> ByteArray?,
) {
    val enabled = connected && item.availability == Availability.AVAILABLE
    val title = mediaTitle(item.name)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(enabled = enabled) { actions.select(item) }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item.thumbnailId != null) MediaThumbnail(item.thumbnailId, thumbnail, Modifier.size(64.dp))
            Column {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.availability !=
                    Availability.AVAILABLE
                ) {
                    Text("Offline", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (item.kind == MediaKind.MUSIC) {
            ItemActions(
                "Options for $title",
                listOf(
                    MenuAction("Add to queue") { actions.enqueue(item) },
                    MenuAction("Add to playlist") { actions.addPlaylist(item) },
                ),
                enabled = connected,
            )
        }
    }
}

@Composable
internal fun PhotoTile(
    photo: IndexedValue<MediaItem>,
    connected: Boolean,
    actions: LibraryActions,
    thumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val item = photo.value
    val title = photoTitle(item.name, photo.index + 1)
    val spoken =
        if (title.startsWith("Photo ")) {
            title.replaceFirstChar { it.lowercase() }
        } else {
            "$title, photo ${photo.index + 1}"
        }
    Surface(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = "Open $spoken" }
            .clickable(enabled = connected && item.availability == Availability.AVAILABLE) { actions.select(item) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        MediaThumbnail(item.thumbnailId, thumbnail, Modifier.fillMaxSize(), fallback = title)
    }
}

@Composable
private fun MediaThumbnail(
    id: String?,
    load: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
    fallback: String? = null,
) {
    val bytes by produceState<ByteArray?>(null, id, load) { value = id?.let { load(it) } }
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap !=
            null
        ) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DockIcon(R.drawable.ic_image, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                fallback?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
