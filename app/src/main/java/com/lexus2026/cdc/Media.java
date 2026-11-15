package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.widget.ImageView;

import java.io.InputStream;

public final class Media {
    private Media() {}

    public static boolean isVideo(Context c, Uri u) {
        String t = c.getContentResolver().getType(u);
        return t != null && t.startsWith("video/");
    }

    public static void loadImage(ImageView iv, Context c, Uri u) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(c.getContentResolver(), u), (decoder, info, src) -> {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    float k = Math.min(1f, 900f / Math.max(1, Math.max(w, h)));
                    decoder.setTargetSize(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)));
                });
                iv.setImageDrawable(d);
                if (d instanceof AnimatedImageDrawable) {
                    AnimatedImageDrawable a = (AnimatedImageDrawable) d;
                    a.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                    a.start();
                }
                return;
            } catch (Throwable e) {}
        }
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            Bitmap bmp = in != null ? BitmapFactory.decodeStream(in, null, o) : null;
            if (bmp != null) iv.setImageBitmap(bmp);
        } catch (Exception e) {}
    }
}
