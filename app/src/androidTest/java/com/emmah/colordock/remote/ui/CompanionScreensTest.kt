package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.companion.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionScreensTest {
    @get:Rule val compose=createComposeRule()
    private val picture=WallpaperMetadata("fixture","Fixture picture","cover","paper","Paper")
    private val snapshot=DockSnapshot(7,WallpaperSettings("fixture",listOf("fixture"),"fixed","open"),picture,0,139,0,false,null,shelfApps.map {it.first},emptyList(),false)
    private val ready=DockState(DockConnectionState.Ready,snapshot)
    private var commands=0
    private fun show(width:Int,scale:Float,tab:RemoteTab=RemoteTab.Dock){
        compose.setContent {val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                RemoteTheme(AppTheme.fromStored(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("theme"))) {Surface {Box(Modifier.width(width.dp)){var current by remember {mutableStateOf(tab)}
                    RemoteAppShell(current,{current=it}) {modifier->
                        if(current==RemoteTab.Dock)DockScreen(ready,DockUiState(),null,{commands++},{commands++},{commands++},{commands++},{_,_,_->commands++},{},{},{},modifier)
                        else if(current==RemoteTab.Wallpapers)WallpaperScreen(ready,WallpaperUiState(),emptyMap(),{commands++},{commands++},{},{},{},{},{commands++},{commands++},{_,_,_->commands++},{commands++},{_,_->},{commands++},{},{},modifier)
                        else androidx.compose.material3.Text("Remote controls",modifier)
                    }
                }}}
            }
        }
    }
    private fun verify(width:Int,scale:Float){
        show(width,scale)
        compose.onNodeWithText("Start clock mode").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start automatically").performScrollTo().performClick()
        compose.onNodeWithText("After 30 minutes").assertIsDisplayed().performClick()
        compose.onNodeWithText("Edit app shelf").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wallpapers").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add from phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Shuffle selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remote",useUnmergedTree=true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Remote controls").assertIsDisplayed()
    }
    @Test fun companionAt320dp()=verify(320,1f)
    @Test fun companionAt200PercentFont()=verify(400,2f)
    @Test fun companionAt320dpAnd200PercentFont()=verify(320,2f)
    @Test fun companionAtDeviceFontScale()=verify(400,InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale)
    @Test fun tabNavigationSendsNoTvCommand(){
        show(400,1f,RemoteTab.Wallpapers)
        compose.onNodeWithText("Remote",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Remote controls").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,commands)}
    }
    @Test fun companionSwitchesExposeTheirNamesAndState(){
        show(320,2f)
        compose.onNodeWithContentDescription("Show weather").performScrollTo().assertIsOff().assertHasClickAction()
        compose.onNodeWithText("Wallpapers").performClick()
        compose.onNodeWithContentDescription("Shuffle selected").performScrollTo().assertIsOff().assertHasClickAction()
    }
    @Test fun builtInCollectionFiltersWithoutSendingTvCommands(){
        val second=picture.copy(id="second",title="Second picture",category="nature",categoryLabel="Nature")
        compose.setContent {RemoteTheme(AppTheme.fromStored(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("theme"))) {WallpaperScreen(ready,WallpaperUiState(source="builtin",items=listOf(picture,second)),emptyMap(),{},{},{},{},{},{},{},{},{_,_,_->},{},{_,_->},{},{},{})}}
        compose.onNodeWithText("Collection").performScrollTo().performClick()
        compose.onNodeWithText("Nature").performClick()
        compose.onNodeWithContentDescription("Second picture").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Fixture picture").assertCountEquals(1) // Current preview stays visible in the list.
    }
    @Test fun shuffleOptionsKeepNextAndTriggerReachable(){
        show(320,2f,RemoteTab.Wallpapers)
        compose.onNodeWithContentDescription("Shuffle options").performScrollTo().performClick()
        compose.onNodeWithText("Change wallpaper").performClick()
        compose.onNodeWithText("On a fresh app start").performClick()
        compose.onNodeWithText("Next wallpaper").performClick()
        compose.runOnIdle {assertEquals(2,commands)}
    }
    @Test fun unavailableCompanionRequiresExplicitOpenAction(){
        compose.setContent {RemoteTheme(AppTheme.fromStored(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("theme"))) {DockUnavailable(DockState(),{commands++},{})}}
        compose.onNodeWithText("Open Color Dock to manage wallpapers").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,commands)}
        compose.onNodeWithText("Open Color Dock").performClick()
        compose.runOnIdle {assertEquals(1,commands)}
    }
    @Test fun connectingMessageUsesReadableEllipsis(){
        compose.setContent {RemoteTheme {DockUnavailable(DockState(DockConnectionState.Connecting),{commands++},{})}}
        compose.onNodeWithText("Connecting to Color Dock\u2026").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,commands)}
    }
    @Test fun staleShelfDraftKeepsOriginalRevisionAndCannotHideEveryApp(){
        var saved:Triple<List<String>,List<String>,Long>?=null
        compose.setContent {RemoteTheme(AppTheme.fromStored(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("theme"))) {AppShelfScreen(snapshot,false,null,{order,hidden,revision->saved=Triple(order,hidden,revision)},{})}}
        compose.onNodeWithContentDescription("Show YouTube").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithContentDescription("Show YouTube").performClick()
        compose.onNodeWithContentDescription("Move YouTube up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move YouTube down").assertIsEnabled()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(7L,saved?.third);assertEquals(shelfApps.map {it.first},saved?.first);assertTrue(saved?.second?.isEmpty()==true)}
    }
}
