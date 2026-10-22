package com.emmah.colordock.remote.ui

import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.companion.*
import com.emmah.colordock.remote.tv.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.io.File

class DesignCaptureTest {
    @get:Rule val compose=createComposeRule()
    private val ids=listOf("layered-magenta-and-turquoise-paper-waves","emerald-mist-abstract-wallpaper","misty-blue-and-white-abstract-flow","vibrant-woven-yarn-wave","retro-floppy-disk-trio","layered-crimson-paper-waves")
    private val pictures=ids.mapIndexed {index,id->WallpaperMetadata("user:00000000-0000-4000-8000-${index.toString().padStart(12,'0')}",id.replace('-',' '),"cover",null,null)}
    private val snapshot=DockSnapshot(7,WallpaperSettings(pictures[0].id,listOf(pictures[0].id,pictures[2].id,pictures[4].id),"shuffle","open"),pictures[0],6,139,0,false,null,shelfApps.map {it.first},emptyList(),false)
    private fun capture(tab:RemoteTab, theme:AppTheme){
        val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
        // Match the reference's 100% type scale without changing the user's device settings.
        val fontScale=InstrumentationRegistry.getArguments().getString("designFontScale")?.toFloat()?:1f
        val images=ids.mapIndexed {index,id->val file=File(context.cacheDir,"design-$id.webp");instrument.context.assets.open("design/$id.webp").use {input->file.outputStream().use {input.copyTo(it)}};pictures[index].id to file}.toMap()
        compose.setContent {val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)){RemoteTheme(theme) {RemoteAppShell(tab,{}){modifier->
            when(tab){
                RemoteTab.Remote->RemoteScreen(TvState(TvConnectionState.Ready,name="Living room TV",muted=false,guideAvailable=true),emptyList(),{},{},{},{},{},{},modifier)
                RemoteTab.Wallpapers->WallpaperScreen(DockState(DockConnectionState.Ready,snapshot),WallpaperUiState(items=pictures),images,{},{},{},{},{},{},{},{},{_,_,_->},{},{_,_->},{},{},{},modifier)
                RemoteTab.Dock->DockScreen(DockState(DockConnectionState.Ready,snapshot),DockUiState(),images[pictures[0].id],{},{},{},{},{_,_,_->},{},{},{},modifier)
            }
        }}}}
        compose.waitForIdle()
        // Let the file-backed image decode complete before taking the visual evidence.
        if(tab!=RemoteTab.Remote)compose.waitUntil(5000){compose.onAllNodes(hasTestTag("wallpaper-image") and (hasContentDescription(pictures[0].title) or hasContentDescription("Current wallpaper clock preview")),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val directory=File(context.cacheDir,"design-captures").apply {mkdirs()}
        val file=File(directory,"${theme.name.lowercase()}-${tab.name.lowercase()}.png")
        compose.onRoot().captureToImage().asAndroidBitmap().let {bitmap->
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            val background=if(theme==AppTheme.WarmLight)OrchidWhite else WarmBlack
            assertEquals("Android must not automatically recolor the selected app theme",background.toArgb(),bitmap.getPixel(1,bitmap.height/2))
        }
        images.values.forEach {it.delete()}
        if(tab==RemoteTab.Remote){
            val navigationTop=compose.onNodeWithTag("bottom-navigation").getUnclippedBoundsInRoot().top
            listOf("Mute","Numbers").forEach {label->
                compose.onNodeWithText(label).assertIsDisplayed()
                val bottom=compose.onNode(hasText(label) and hasClickAction()).getUnclippedBoundsInRoot().bottom
                assertTrue("$label must leave 24 dp above navigation at fontScale=$fontScale",navigationTop-bottom>=24.dp)
            }
        }else{
            val tag=if(tab==RemoteTab.Wallpapers)"shuffle-settings" else "app-shelf-entry"
            val bottom=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom
            val navigationTop=compose.onNodeWithTag("bottom-navigation").getUnclippedBoundsInRoot().top
            assertTrue("Complete $tag bottom=$bottom must fit above navigation=$navigationTop at fontScale=$fontScale",bottom<=navigationTop)
        }
    }
    @Test fun remoteLight()=capture(RemoteTab.Remote,AppTheme.WarmLight)
    @Test fun remoteDark()=capture(RemoteTab.Remote,AppTheme.SoftDark)
    @Test fun wallpapersLight()=capture(RemoteTab.Wallpapers,AppTheme.WarmLight)
    @Test fun wallpapersDark()=capture(RemoteTab.Wallpapers,AppTheme.SoftDark)
    @Test fun dockLight()=capture(RemoteTab.Dock,AppTheme.WarmLight)
    @Test fun dockDark()=capture(RemoteTab.Dock,AppTheme.SoftDark)
}
