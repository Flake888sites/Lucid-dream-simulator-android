package com.luciddream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import image.Features;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Reads an image file for {@link Features}. JPEG/PNG/WebP are decoded a band of rows at a time, so a 50 MP photo never has to
 * fit in memory; anything else (GIF, BMP...) is decoded whole, and shrunk first if it is really huge.
 */
final class AndroidImage implements Features.Source {
    private BitmapRegionDecoder region;
    private int[] full;
    private int w, h;

    AndroidImage(File f) throws IOException {
        String path = f.getAbsolutePath();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("Unsupported image");
        w = o.outWidth; h = o.outHeight;
        try {
            region = BitmapRegionDecoder.newInstance(path, false);
        } catch (IOException | RuntimeException e) {
            region = null;
        }
        if (region == null) {
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inPreferredConfig = Bitmap.Config.ARGB_8888;
            d.inPremultiplied = false;
            d.inSampleSize = 1;
            while ((long) (w / d.inSampleSize) * (h / d.inSampleSize) > 6_000_000L) d.inSampleSize *= 2;
            Bitmap bm = BitmapFactory.decodeFile(path, d);
            if (bm == null) throw new IOException("Unsupported image");
            w = bm.getWidth(); h = bm.getHeight();
            full = new int[w * h];
            bm.getPixels(full, 0, w, 0, 0, w, h);
            bm.recycle();
        }
    }

    @Override public int width() { return w; }
    @Override public int height() { return h; }

    @Override public int[] rows(int y0, int y1) throws IOException {
        if (full != null) return Arrays.copyOfRange(full, y0 * w, y1 * w);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inPremultiplied = false;
        Bitmap bm = region.decodeRegion(new Rect(0, y0, w, y1), o);
        if (bm == null || bm.getWidth() != w || bm.getHeight() != y1 - y0) throw new IOException("Could not read the image");
        int[] px = new int[w * (y1 - y0)];
        bm.getPixels(px, 0, w, 0, 0, w, y1 - y0);
        bm.recycle();
        return px;
    }
}
