package de.geeksfactory.opacclient.networking;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.HashSet;

import de.geeksfactory.opacclient.objects.CoverHolder;
import de.geeksfactory.opacclient.utils.Base64;
import de.geeksfactory.opacclient.utils.ISBNTools;

public class CoverDownloadTask extends AsyncTask<Void, Integer, CoverHolder> {
    static HashSet<String> rejectImages = new HashSet<>();
    protected int width = 0;
    protected int height = 0;

    static {
        rejectImages.add(
                "R0lGODlhOwBLAIAAALy8vf///yH5BAEAAAEALAAAAAA7AEsAAAL/jI+py+0Po5y0" +
                        "2ouz3rz7D2rASJbmiYYGyralGrhyqrbTW4+rGeEhmeA5fCCg4sQgfowLFkLpYTaE" +
                        "O10OIJFCO9KhtYq9Zr+xbpTsDYNh5iR5y2k33/JNPUhHn9UP7T3zd+Cnx0U4xwdn" +
                        "Z3iUx7e0iIcYeDFZJgkJiCnYyKZZ9VRZUTnouDd2WVqYegjqaTHKebUa6SSLKdOJ" +
                        "5GYDY0nVWtvrqxSa61PciytMwbss+uvMjBxNXW19jZ29bHVJu/MNvqmTCK4WhvbF" +
                        "bS65EnPqXiaIJ26Eg/6HVW8+327fHg9kVpBw5xylc6eu3jeBTwh28bewIJh807RZ" +
                        "vIgxo8aNRxw7ZlNXbt04RvT+lXQjL57KciT/nRuY5iW8YzJPQjx5xKVCeCoNurTE" +
                        "0+QukBNZAsu3ECbKnhIBBnwaMWFBVx6rWr2KdUIBADs=");
        rejectImages
                .add("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");
    }

    protected CoverHolder item;
    protected Context context;

    public CoverDownloadTask(Context context, CoverHolder item) {
        this.item = item;
        this.context = context;
    }

    @Override
    protected CoverHolder doInBackground(Void... voids) {
        if (item.getCover() != null && item.getCoverBitmap() == null) {
            try {
                HttpClient http_client = new AndroidHttpClientFactory()
                        .getNewApacheHttpClient(false, true, false);

                if (width == 0 && height == 0) {
                    // Use default
                    float density = context.getResources().getDisplayMetrics().density;
                    width = height = (int) density * 56;
                }

                HttpGet httpget = new HttpGet(ISBNTools.getBestSizeCoverUrl(item.getCover(),
                        width, height));
                HttpResponse response;

                try {
                    response = http_client.execute(httpget);

                    if (response.getStatusLine().getStatusCode() >= 400) {
                        item.setCover(null);
                    }
                    HttpEntity entity = response.getEntity();
                    byte[] bytes = EntityUtils.toByteArray(entity);
                    if (rejectImages.contains(Base64.encodeBytes(bytes))) {
                        // OPACs like VuFind have a 'cover proxy' that returns a simple GIF with
                        // the text 'no image available' if no cover was found. We don't want to
                        // display this image but the media type,
                        // so we detect it. We do this here
                        // instead of in the API implementation because only this way it can be
                        // done asynchronously.
                        item.setCover(null);
                    } else {
                        if (bytes.length > 64) {
                            item.setCoverBitmap(bytes);
                        } else {
                            // When images embedded from Amazon aren't available, a
                            // 1x1
                            // pixel image is returned (iOPAC)
                            item.setCover(null);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (OutOfMemoryError e) {
                item.setCoverBitmap(null);
                item.setCover(null);
                Log.i("CoverDownloadTask", "OutOfMemoryError");
                return item;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return item;
    }
}
