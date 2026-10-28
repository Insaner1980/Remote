package com.emmah.colordock.remote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNativeBitmapFactory;

/** Keeps native decoding without retaining a Windows file handle in the graphics runtime. */
@Implements(value = BitmapFactory.class, callNativeMethodsByDefault = true)
public class JvmBitmapFactory extends ShadowNativeBitmapFactory {
    @Implementation
    protected static Bitmap decodeFile(String path, BitmapFactory.Options options) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (IOException ignored) {
            return null;
        }
    }
}
