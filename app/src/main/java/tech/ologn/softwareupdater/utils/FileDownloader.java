package tech.ologn.softwareupdater.utils;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Downloads chunk of a file from given url using {@code offset} and {@code size},
 * and saves to a given location.
 *
 * In a real-life application this helper class should download from HTTP Server,
 * but in this sample app it will only download from a local file.
 */
public final class FileDownloader {

    private String mUrl;
    private long mOffset;
    private long mSize;
    private File mDestination;

    public FileDownloader(String url, long offset, long size, File destination) {
        this.mUrl = url;
        this.mOffset = offset;
        this.mSize = size;
        this.mDestination = destination;
    }

    /**
     * Downloads the file with given offset and size.
     * If size is -1, downloads the entire file from offset to end.
     * @throws IOException when can't download the file
     */
    public void download() throws IOException {
        Log.d("FileDownloader", "downloading " + mDestination.getName()
                + " from " + mUrl
                + " to " + mDestination.getAbsolutePath()
                + " offset=" + mOffset
                + " size=" + (mSize == -1 ? "unlimited" : mSize));

        URL url = new URL(mUrl);
        URLConnection connection = url.openConnection();
        connection.connect();

        // download the file
        try (InputStream input = connection.getInputStream()) {
            try (OutputStream output = new FileOutputStream(mDestination)) {
                long skipped = input.skip(mOffset);
                if (skipped != mOffset) {
                    throw new IOException("Can't download file "
                            + mUrl
                            + " with given offset "
                            + mOffset);
                }

                byte[] data = new byte[4096];
                long total = 0;
                long lastLogTime = System.currentTimeMillis();
                final long LOG_INTERVAL = 3000; // Log every 3 seconds

                if (mSize == -1) {
                    int count;
                    while ((count = input.read(data)) != -1) {
                        output.write(data, 0, count);
                        total += count;

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastLogTime >= LOG_INTERVAL) {
                            double mbDownloaded = total / 1024.0 / 1024.0;
                            Log.d("FileDownloader", "Download progress: " + String.format("%.2f", mbDownloaded) + " MB downloaded");
                            lastLogTime = currentTime;
                        }
                    }
                    double totalMb = total / 1024.0 / 1024.0;
                    Log.d("FileDownloader", "Downloaded entire file: " + String.format("%.2f", totalMb) + " MB");
                } else {
                    while (total < mSize) {
                        int needToRead = (int) Math.min(4096, mSize - total);
                        int count = input.read(data, 0, needToRead);
                        if (count <= 0) {
                            break;
                        }
                        output.write(data, 0, count);
                        total += count;

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastLogTime >= LOG_INTERVAL) {
                            int progressPercent = (int) ((total * 100) / mSize);
                            double downloadedMb = total / 1024.0 / 1024.0;
                            double totalMb = mSize / 1024.0 / 1024.0;
                            Log.d("FileDownloader", "Download progress: " + progressPercent + "% (" + String.format("%.2f", downloadedMb) + " MB / " + String.format("%.2f", totalMb) + " MB)");
                            lastLogTime = currentTime;
                        }
                    }

                    double totalMb = total / 1024.0 / 1024.0;
                    Log.d("FileDownloader", "Download completed: with total size " + String.format("%.2f", totalMb) + " MB");

                    if (total != mSize) {
                        throw new IOException("Can't download file "
                                + mUrl
                                + " with given size "
                                + mSize);
                    }
                }
            }
        }
    }

}
