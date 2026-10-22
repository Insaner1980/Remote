package com.emmah.colordock.remote.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.R
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRenderingTest {
    @Test fun convertedMaterialSymbolsHaveVisiblePixels(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val icons=listOf(R.drawable.ic_home,R.drawable.ic_grid_view,R.drawable.ic_power_settings_new,R.drawable.ic_tv,R.drawable.ic_keyboard_arrow_up,R.drawable.ic_keyboard_arrow_down,R.drawable.ic_keyboard_arrow_left,R.drawable.ic_keyboard_arrow_right,R.drawable.ic_undo,R.drawable.ic_format_list_bulleted,R.drawable.ic_volume_up,R.drawable.ic_volume_off,R.drawable.ic_add,R.drawable.ic_remove,R.drawable.ic_settings_remote,R.drawable.ic_image,R.drawable.ic_play_arrow,R.drawable.ic_shuffle,R.drawable.ic_check,R.drawable.ic_close,R.drawable.ic_delete,R.drawable.ic_edit,R.drawable.ic_refresh)
        for(id in icons){val bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);try{
            val drawable=requireNotNull(context.getDrawable(id));drawable.setBounds(0,0,48,48);drawable.draw(Canvas(bitmap))
            val pixels=IntArray(48*48);bitmap.getPixels(pixels,0,48,0,0,48,48)
            assertTrue("The icon must be visible: ${context.resources.getResourceEntryName(id)}",pixels.count {(it ushr 24)>0}>20)
        }finally{bitmap.recycle()}}
    }
}
