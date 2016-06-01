package de.geeksfactory.opacclient.networking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.bitmap.StreamBitmapDecoder;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.BitmapImageViewTarget;

import java.util.HashMap;

import de.geeksfactory.opacclient.utils.ISBNTools;

public class GlideCoverLoader {
    private final Context context;
    private static final int REJECT_IMAGES_MAX_SIZE = 20;
    private HashMap<String, Bitmap> rejectImages;

    public GlideCoverLoader(Context context) {
        this.context = context;
    }

    public void loadCover(ImageView iv, String url, Drawable placeholderDrawable,
            final Drawable errorDrawable) {
        int width = iv.getWidth();
        int height = iv.getHeight();

        if (width == 0 && height == 0) {
            // Use default
            float density = context.getResources().getDisplayMetrics().density;
            width = height = (int) density * 56;
        }
        String bestUrl = ISBNTools.getBestSizeCoverUrl(url, width, height);

        Glide glide = Glide.get(context);
        Glide.with(context)
             .load(bestUrl).asBitmap().imageDecoder(new StreamBitmapDecoder(
                GlideCustomDownsampler.AT_LEAST, glide.getBitmapPool(), DecodeFormat.DEFAULT))
             .placeholder(placeholderDrawable).error(errorDrawable)
             .into(new BitmapImageViewTarget(iv) {
                 @Override
                 public void onResourceReady(Bitmap bm,
                         GlideAnimation<? super Bitmap> animation) {
                     int width = bm.getWidth();
                     int height = bm.getHeight();
                     if (Math.max(width, height) <= REJECT_IMAGES_MAX_SIZE) {
                         this.onLoadFailed(null, errorDrawable);
                         return;
                     }

                     super.onResourceReady(bm, animation);
                 }
             });
    }
}
