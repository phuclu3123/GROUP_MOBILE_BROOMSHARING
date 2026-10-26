package vn.edu.ueh.phuclu.broomsharing.utils;

import android.content.Context;
import android.net.Uri;
import android.widget.ImageView;

import com.squareup.picasso.*;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.repository.RemoteImageClient;

// Áp dụng Picasso của bài tập cho avatar và ảnh chọn trong hồ sơ; dùng chung OkHttp có giới hạn
// mạng.
public final class OnlineImages {
    private static Picasso images;

    private OnlineImages() {}

    public static synchronized Picasso get(Context context) {
        if (images == null)
            images =
                    new Picasso.Builder(context.getApplicationContext())
                            .downloader(new OkHttp3Downloader(RemoteImageClient.client()))
                            .build();
        return images;
    }

    public static void show(ImageView view, Uri uri) {
        get(view.getContext())
                .load(uri)
                .resize(800, 800)
                .centerInside()
                .placeholder(R.drawable.ic_photo)
                .error(R.drawable.ic_photo)
                .into(view);
    }
}
