package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

import java.io.File;

/**
 * Local profile cover shown behind the profile header.
 *
 * The selected image is kept in the app's private files directory. This view
 * deliberately does not upload the image or change Telegram profile data.
 */
public class ProfileBannerView extends FrameLayout {

    private final ImageView imageView;
    private final TextView actionView;
    private int imageLoadGeneration;
    private boolean hasImage;

    public ProfileBannerView(Context context) {
        super(context);
        setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF372D55, 0xFF815274, 0xFFE98BAE}
        ));
        setClipChildren(true);
        setClipToPadding(true);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(imageView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xB8000000, 0x00000000}
        ));
        addView(shade, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        actionView = new TextView(context);
        actionView.setTextColor(Color.WHITE);
        actionView.setTextSize(12);
        actionView.setTypeface(Typeface.DEFAULT_BOLD);
        actionView.setGravity(Gravity.CENTER);
        actionView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x66000000);
        pill.setCornerRadius(AndroidUtilities.dp(16));
        actionView.setBackground(pill);

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                AndroidUtilities.dp(32),
                Gravity.BOTTOM | Gravity.END
        );
        actionParams.setMargins(0, 0, AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        addView(actionView, actionParams);
        clearImage();
    }

    public boolean hasImage() {
        return hasImage;
    }

    public void setImageFile(File file) {
        final int generation = ++imageLoadGeneration;
        if (file == null || !file.isFile()) {
            clearImage();
            return;
        }

        hasImage = true;
        actionView.setText(R.string.ProfileBannerChange);
        Thread loader = new Thread(() -> {
            Bitmap bitmap = decode(file);
            post(() -> {
                if (generation != imageLoadGeneration) {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    return;
                }
                if (bitmap == null) {
                    hasImage = false;
                    imageView.setImageDrawable(null);
                    actionView.setText(R.string.ProfileBannerAdd);
                    return;
                }
                imageView.setImageBitmap(bitmap);
            });
        }, "ProfileBannerDecode");
        loader.setDaemon(true);
        loader.start();
    }

    public void clearImage() {
        imageLoadGeneration++;
        hasImage = false;
        imageView.setImageDrawable(null);
        actionView.setText(R.string.ProfileBannerAdd);
    }

    private static Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1000) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }
}
