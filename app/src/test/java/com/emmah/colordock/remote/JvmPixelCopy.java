package com.emmah.colordock.remote;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.Window;
import androidx.test.platform.app.InstrumentationRegistry;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPixelCopy;

/** Robolectric's API 37 surface capture requires the main thread. */
@Implements(PixelCopy.class)
public class JvmPixelCopy extends ShadowPixelCopy {
    @Implementation(methodName = "request")
    protected static void requestOnMainThread(
            Window source, Rect bounds, Bitmap destination,
            PixelCopy.OnPixelCopyFinishedListener listener, Handler handler) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ShadowPixelCopy.request(source, bounds, destination, listener, handler);
        } else {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> ShadowPixelCopy.request(source, bounds, destination, listener, handler));
        }
    }
}
