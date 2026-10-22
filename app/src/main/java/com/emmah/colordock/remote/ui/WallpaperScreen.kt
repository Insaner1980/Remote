package com.emmah.colordock.remote.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.companion.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun Picture(file:File?,label:String,fit:String="cover",modifier:Modifier=Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,file?.path) {value=withContext(Dispatchers.IO){file?.let {runCatching {BitmapFactory.decodeFile(it.path)?.asImageBitmap()}.getOrNull()}}}
    Surface(modifier, color=MaterialTheme.colorScheme.background) {
        if(bitmap!=null)Image(requireNotNull(bitmap),label,Modifier.fillMaxSize().testTag("wallpaper-image"),contentScale=if(fit=="contain")ContentScale.Fit else ContentScale.Crop)
        else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){DockIcon(R.drawable.ic_image,label,tint=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable
internal fun DockUnavailable(state:DockState,onOpen:()->Unit,onRetry:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(if(state.connection==DockConnectionState.Connecting)"Connecting to Color Dock\u2026" else "Open Color Dock to manage wallpapers",style=MaterialTheme.typography.titleMedium)
        state.message?.let {Text(it)}
        Button(onOpen,Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Open Color Dock")}
        TextButton(onRetry,Modifier.fillMaxWidth().heightIn(min=48.dp)){DockIcon(R.drawable.ic_refresh);Spacer(Modifier.width(8.dp));Text("Try again")}
    }
}

@Composable
fun WallpaperScreen(state:DockState,ui:WallpaperUiState,thumbnails:Map<String,File>,onOpen:()->Unit,onJoin:()->Unit,onSource:(String)->Unit,onMore:()->Unit,onThumbnail:(WallpaperMetadata)->Unit,onDetail:(WallpaperMetadata?)->Unit,onAdd:()->Unit,onPatch:(DockPatch)->Unit,onImageAction:(DockMethod,WallpaperMetadata,String?)->Unit,onNext:()->Unit,onPreparedFit:(String,String)->Unit,onSend:()->Unit,onCancelUpload:()->Unit,onAbandon:()->Unit,modifier:Modifier=Modifier,onSettings:()->Unit={}) {
    val snapshot=state.snapshot
    var deleteConfirmation by remember {mutableStateOf(false)}
    var shuffleOptions by remember {mutableStateOf(false)}
    var category by remember(ui.source) {mutableStateOf("all")}
    val detail=ui.detail
    BackHandler(detail!=null||ui.prepared.isNotEmpty()){if(detail!=null)onDetail(null)else onAbandon()}
    if(detail!=null&&snapshot!=null) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            TextButton({onDetail(null)}){DockIcon(R.drawable.ic_undo);Spacer(Modifier.width(8.dp));Text("Wallpapers")}
            Text(detail.title,style=MaterialTheme.typography.headlineSmall)
            LaunchedEffect(detail.id){onThumbnail(detail)}
            Picture(thumbnails[detail.id],detail.title,detail.fit,Modifier.fillMaxWidth().aspectRatio(16f/9).clip(RoundedCornerShape(14.dp)))
            Button({onImageAction(DockMethod.Use,detail,null)},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!ui.saving){Text("Use wallpaper")}
            OutlinedButton({val pool=snapshot.wallpaper.pool;onPatch(DockPatch.Wallpaper(pool=if(detail.id in pool)pool-detail.id else pool+detail.id))},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!ui.saving){Text(if(detail.id in snapshot.wallpaper.pool)"Remove from shuffle" else "Add to shuffle")}
            if(detail.personal) {
                ChoiceRow("Picture fit",listOf("cover" to "Fill screen","contain" to "Fit whole picture"),detail.fit,!ui.saving){onImageAction(DockMethod.Fit,detail,it)}
                TextButton({deleteConfirmation=true},Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!ui.saving){DockIcon(R.drawable.ic_delete,tint=MaterialTheme.colorScheme.error);Spacer(Modifier.width(8.dp));Text("Delete from TV",color=MaterialTheme.colorScheme.error)}
            }
            ui.error?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
        }
        if(deleteConfirmation)AlertDialog(onDismissRequest={deleteConfirmation=false},title={Text("Delete this picture from the TV?")},text={Text("The original picture on your phone will be kept.")},confirmButton={TextButton({deleteConfirmation=false;onImageAction(DockMethod.Delete,detail,null)}){Text("Delete")}},dismissButton={TextButton({deleteConfirmation=false}){Text("Cancel")}})
        return
    }
    if(ui.prepared.isNotEmpty()||ui.preparing) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Preview pictures",style=MaterialTheme.typography.headlineSmall)
            Text("Fill screen may crop the picture. Fit whole picture keeps the complete image.")
            if(ui.preparing)LinearProgressIndicator(Modifier.fillMaxWidth())
            for(image in ui.prepared) {
                Text(image.name,style=MaterialTheme.typography.titleMedium)
                Picture(image.image,image.name,image.fit,Modifier.fillMaxWidth().aspectRatio(16f/9).clip(RoundedCornerShape(14.dp)))
                ChoiceRow("Picture fit",listOf("cover" to "Fill screen","contain" to "Fit whole picture"),image.fit,ui.upload==null){onPreparedFit(image.uploadId,it)}
            }
            ui.upload?.let {progress->Text(progress.stage,Modifier.semantics {liveRegion=LiveRegionMode.Polite});LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.fillMaxWidth());progress.message?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}}
            ui.outcome?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite})};ui.error?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
            if(ui.upload==null)Button(onSend,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!ui.preparing){Text(if(ui.outcome?.contains("Failed:")==true)"Retry failed" else "Send to TV")}
            else OutlinedButton(onCancelUpload,Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Cancel upload")}
            TextButton(onAbandon,Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Cancel")}
        }
        return
    }
    LazyVerticalGrid(columns=GridCells.Adaptive(140.dp),modifier=modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item(span={GridItemSpan(maxLineSpan)}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Wallpapers",style=MaterialTheme.typography.headlineSmall);Text("Pictures stored on your TV",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            SettingsButton(onSettings)}}
        if(state.connection!=DockConnectionState.Ready||snapshot==null) {
            item(span={GridItemSpan(maxLineSpan)}){DockUnavailable(state,onOpen,onJoin)}
        } else {
            item(span={GridItemSpan(maxLineSpan)}){
                LaunchedEffect(snapshot.current.id){onThumbnail(snapshot.current)}
                OutlinedCard(onClick={onDetail(snapshot.current)},shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                    Box {Picture(thumbnails[snapshot.current.id],snapshot.current.title,snapshot.current.fit,Modifier.fillMaxWidth().aspectRatio(2.8f));Surface(Modifier.align(Alignment.BottomStart).padding(10.dp),RoundedCornerShape(20.dp),WarmBlack.copy(alpha=.85f),contentColor=OrchidWhite){Text("In use",Modifier.padding(horizontal=12.dp,vertical=5.dp),style=MaterialTheme.typography.labelLarge)}}
                }
            }
            item(span={GridItemSpan(maxLineSpan)}){Button(onAdd,Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer)){DockIcon(R.drawable.ic_add);Spacer(Modifier.width(10.dp));Text("Add from phone")}}
            item(span={GridItemSpan(maxLineSpan)}){
                Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(2.dp),space=0.dp) {
                        listOf("personal" to "My pictures","builtin" to "Built-in").forEach {(value,label)->SegmentedButton(ui.source==value,{onSource(value)},RoundedCornerShape(24.dp),border=BorderStroke(0.dp,androidx.compose.ui.graphics.Color.Transparent),colors=SegmentedButtonDefaults.colors(activeContainerColor=MaterialTheme.colorScheme.secondaryContainer,activeContentColor=MaterialTheme.colorScheme.onSecondaryContainer,inactiveContainerColor=MaterialTheme.colorScheme.surface),icon={}){Text(label)}}
                    }
                }
            }
            if(ui.source=="builtin")item(span={GridItemSpan(maxLineSpan)}) {
                ChoiceRow("Collection",listOf("all" to "All collections")+ui.items.mapNotNull {image->image.category?.let {it to (image.categoryLabel?:it)}}.distinct(),category,!ui.loading){category=it}
            }
            if(ui.items.isEmpty()&&!ui.loading)item(span={GridItemSpan(maxLineSpan)}){Text(if(ui.source=="personal")"Add pictures from your phone to use them as TV wallpapers." else "No built-in pictures are available. Try again.",Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(ui.items.filter {ui.source!="builtin"||category=="all"||it.category==category},key={it.id}){image->
                LaunchedEffect(image.id){onThumbnail(image)}
                val selected=image.id in snapshot.wallpaper.pool
                OutlinedCard(onClick={onDetail(image)},shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.semantics {stateDescription=if(selected)"Selected for shuffle" else "Not selected for shuffle"}) {
                    Box {Picture(thumbnails[image.id],image.title,image.fit,Modifier.fillMaxWidth().aspectRatio(2.25f));if(selected)Surface(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp),RoundedCornerShape(50),MaterialTheme.colorScheme.primaryContainer){DockIcon(R.drawable.ic_check,tint=MaterialTheme.colorScheme.onPrimaryContainer)}}
                }
            }
            if(ui.loading)item(span={GridItemSpan(maxLineSpan)}){LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(ui.cursor!=null)item(span={GridItemSpan(maxLineSpan)}){TextButton(onMore,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!ui.loading){Text("Load more pictures")}}
            item(span={GridItemSpan(maxLineSpan)}){
                OutlinedCard(modifier=Modifier.testTag("shuffle-settings"),shape=RoundedCornerShape(14.dp)) {Column(Modifier.padding(horizontal=14.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically){Text("${snapshot.wallpaper.pool.size} pictures selected",Modifier.weight(1f));IconButton({shuffleOptions=true}){DockIcon(R.drawable.ic_keyboard_arrow_right,"Shuffle options")}}
                    HorizontalDivider()
                    Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){DockIcon(R.drawable.ic_shuffle);Text("Shuffle selected",Modifier.weight(1f).padding(horizontal=10.dp));Switch(snapshot.wallpaper.mode=="shuffle",{onPatch(DockPatch.Wallpaper(mode=if(it)"shuffle" else "fixed"))},modifier=Modifier.semantics {contentDescription="Shuffle selected"},enabled=!ui.saving)}
                }}
            }
            ui.error?.let {message->item(span={GridItemSpan(maxLineSpan)}){Text(message,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}}
            ui.outcome?.let {message->item(span={GridItemSpan(maxLineSpan)}){Text(message,Modifier.semantics {liveRegion=LiveRegionMode.Polite})}}
        }
    }
    if(shuffleOptions&&snapshot!=null)AlertDialog(onDismissRequest={shuffleOptions=false},title={Text("Shuffle options")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        ChoiceRow("Change wallpaper",listOf("open" to "Whenever Color Dock opens","start" to "On a fresh app start"),snapshot.wallpaper.trigger,!ui.saving){onPatch(DockPatch.Wallpaper(trigger=it))}
        OutlinedButton(onNext,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!ui.saving){Text("Next wallpaper")}
    }},confirmButton={TextButton({shuffleOptions=false}){Text("Done")}})
}

@Composable
internal fun ChoiceRow(label:String,choices:List<Pair<String,String>>,selected:String,enabled:Boolean=true,onSelect:(String)->Unit) {
    var open by remember {mutableStateOf(false)}
    Column {
        OutlinedButton({open=true},Modifier.fillMaxWidth().heightIn(min=54.dp),enabled=enabled,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),colors=ButtonDefaults.outlinedButtonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
            val value=choices.firstOrNull {it.first==selected}?.second?:selected
            BoxWithConstraints(Modifier.weight(1f)) {
                if(maxWidth<260.dp||LocalDensity.current.fontScale>1.3f)Column(verticalArrangement=Arrangement.spacedBy(2.dp)){Text(label,style=MaterialTheme.typography.bodyMedium);Text(value,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(value,Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            DockIcon(R.drawable.ic_keyboard_arrow_right)
        }
        DropdownMenu(open,{open=false}) {choices.forEach {(value,text)->DropdownMenuItem(text={Text(text)},onClick={open=false;onSelect(value)})}}
    }
}
