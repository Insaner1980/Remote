package com.emmah.colordock.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.companion.*
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val shelfApps=listOf("youtube.leanback.v4" to "YouTube","netflix" to "Netflix","com.wbd.stream" to "HBO Max","viaplay-lg" to "Viaplay","fi.yle.areena" to "Yle Areena","mtvkatsomo" to "MTV Katsomo","fi.nelonenmedia.ruutu" to "Ruutu","cdp-30" to "Plex")

@Composable
fun DockScreen(state:DockState,ui:DockUiState,thumbnail:File?,onOpen:()->Unit,onJoin:()->Unit,onClock:()->Unit,onPatch:(DockPatch)->Unit,onShelf:(List<String>,List<String>,Long)->Unit,onQuery:(String)->Unit,onSearch:()->Unit,onLocation:(WeatherLocation)->Unit,modifier:Modifier=Modifier,onSettings:()->Unit={}) {
    var page by rememberSaveable {mutableStateOf("home")}
    val snapshot=state.snapshot
    BackHandler(page!="home"){page="home"}
    if(page=="shelf"&&snapshot!=null){AppShelfScreen(snapshot,ui.saving,ui.error,onSave=onShelf,onCancel={page="home"},modifier);return}
    if(page=="location") {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            TextButton({page="home"}){DockIcon(R.drawable.ic_undo);Spacer(Modifier.width(8.dp));Text("Color Dock")}
            Text("Choose location",style=MaterialTheme.typography.headlineSmall)
            OutlinedTextField(ui.query,onQuery,Modifier.fillMaxWidth(),label={Text("City or place")},singleLine=true)
            Button(onSearch,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=ui.query.trim().length>=2&&!ui.searching){Text("Search")}
            if(ui.searching)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.searched&&ui.results.isEmpty())Text("No locations found. Try another city or place name.")
            ui.results.forEach {location->OutlinedCard(onClick={onLocation(location);page="home"},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text(location.label,Modifier.padding(16.dp))}}
            ui.error?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
            Text("Weather data: Open-Meteo",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        };return
    }
    var clock by remember {mutableStateOf(LocalTime.now())}
    LaunchedEffect(Unit){while(true){clock=LocalTime.now();delay(1000)}}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Color Dock",style=MaterialTheme.typography.headlineSmall);Text("Home screen settings",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            SettingsButton(onSettings)}
        HorizontalDivider(Modifier.padding(vertical=2.dp))
        if(state.connection!=DockConnectionState.Ready||snapshot==null){DockUnavailable(state,onOpen,onJoin);return@Column}
        Text("Clock mode",style=MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(14.dp))) {
            Picture(thumbnail,"Current wallpaper clock preview",snapshot.current.fit,Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.Center),RoundedCornerShape(12.dp),WarmBlack.copy(alpha=.65f),contentColor=OrchidWhite){Text(clock.format(DateTimeFormatter.ofPattern("HH:mm",Locale.ENGLISH)),Modifier.padding(8.dp),style=MaterialTheme.typography.displayMedium)}
        }
        Button(onClock,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!ui.saving,shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(contentColor=MaterialTheme.colorScheme.onPrimaryContainer,containerColor=MaterialTheme.colorScheme.primaryContainer)) {
            DockIcon(if(snapshot.clockActive)R.drawable.ic_close else R.drawable.ic_play_arrow);Spacer(Modifier.width(10.dp));Text(if(snapshot.clockActive)"Stop clock mode" else "Start clock mode")
        }
        ChoiceRow("Start automatically",listOf("0" to "Off","5" to "After 5 minutes","10" to "After 10 minutes","30" to "After 30 minutes"),snapshot.idleMinutes.toString(),!ui.saving){onPatch(DockPatch.Clock(it.toInt()))}
        Text("Press OK or Back on the TV remote to leave clock mode.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Weather",style=MaterialTheme.typography.titleMedium)
        OutlinedCard(shape=RoundedCornerShape(14.dp)) {Column(Modifier.padding(horizontal=14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically){Text("Show weather",Modifier.weight(1f));Switch(snapshot.weatherEnabled,{enabled->if(enabled&&snapshot.location==null)page="location" else onPatch(DockPatch.Weather(enabled,snapshot.location))},modifier=Modifier.semantics {contentDescription="Show weather"},enabled=!ui.saving)}
            HorizontalDivider()
            TextButton({page="location"},Modifier.fillMaxWidth().heightIn(min=60.dp),enabled=!ui.saving,shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(vertical=8.dp),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onSurface)){Column(Modifier.weight(1f)){Text("Location");Text(snapshot.location?.label?:"Choose location",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)};DockIcon(R.drawable.ic_keyboard_arrow_right)}
        }}
        Text("Apps",style=MaterialTheme.typography.titleMedium)
        OutlinedCard(onClick={page="shelf"},modifier=Modifier.fillMaxWidth().testTag("app-shelf-entry"),shape=RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=52.dp).padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text("Edit app shelf",Modifier.weight(1f));DockIcon(R.drawable.ic_keyboard_arrow_right)}
        }
        if(ui.saving)Text("Saving\u2026")
        ui.error?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
    }
}

@Composable
fun AppShelfScreen(snapshot:DockSnapshot,saving:Boolean,error:String?,onSave:(List<String>,List<String>,Long)->Unit,onCancel:()->Unit,modifier:Modifier=Modifier) {
    var order by rememberSaveable {mutableStateOf(snapshot.shelfOrder)}
    var hidden by rememberSaveable {mutableStateOf(snapshot.shelfHidden)}
    val revision=rememberSaveable {snapshot.revision}
    var localError by remember {mutableStateOf<String?>(null)}
    var saveRequested by remember {mutableStateOf(false)}
    LaunchedEffect(snapshot.revision,saving,error,saveRequested){
        if(saveRequested&&!saving){
            if(error!=null)saveRequested=false
            else if(snapshot.revision>revision&&snapshot.shelfOrder==order&&snapshot.shelfHidden.toSet()==hidden.toSet())onCancel()
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Edit app shelf",style=MaterialTheme.typography.headlineSmall)
        Text("Choose the order of your Color Dock apps.")
        Text("Preview: "+order.filterNot {it in hidden}.joinToString(", "){id->shelfApps.first {it.first==id}.second},style=MaterialTheme.typography.bodySmall)
        order.forEachIndexed {index,id->val name=shelfApps.first {it.first==id}.second
            OutlinedCard(shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Text(name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Switch(id !in hidden,{visible->if(!visible&&hidden.size==order.size-1)localError="Keep at least one app visible." else {hidden=if(visible)hidden-id else hidden+id;localError=null}},modifier=Modifier.semantics {contentDescription="Show $name"},enabled=!saving)}
                Row {TextButton({order=order.toMutableList().apply {add(index-1,removeAt(index))}},enabled=index>0&&!saving,modifier=Modifier.weight(1f).semantics {contentDescription="Move $name up"}){DockIcon(R.drawable.ic_keyboard_arrow_up);Text("Move up")};TextButton({order=order.toMutableList().apply {add(index+1,removeAt(index))}},enabled=index<order.lastIndex&&!saving,modifier=Modifier.weight(1f).semantics {contentDescription="Move $name down"}){DockIcon(R.drawable.ic_keyboard_arrow_down);Text("Move down")}}
            }}
        }
        (error?:localError)?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
        Button({saveRequested=true;onSave(order,hidden,revision)},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!saving){Text(if(saving)"Saving\u2026" else "Save")}
        TextButton(onCancel,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!saving){Text("Cancel")}
    }
}
