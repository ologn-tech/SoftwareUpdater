/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.ologn.softwareupdater.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import tech.ologn.softwareupdater.MainActivity;
import tech.ologn.softwareupdater.R;
import tech.ologn.softwareupdater.UpdateConfig;
import tech.ologn.softwareupdater.utils.SystemPropertiesHelper;
import tech.ologn.softwareupdater.utils.UpdateConfigs;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service for downloading config files that continues running even when app is destroyed.
 */
public class ForegroundConfigDownloadService extends Service {

    private static final String TAG = "ForegroundConfigDownload";
    private static final String CHANNEL_ID = "config_download_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB max config file size

    // Intent extras
    public static final String EXTRA_CONFIG_URL = "config_url";
    public static final String EXTRA_DOWNLOAD_ID = "download_id";

    // Broadcast actions
    public static final String ACTION_DOWNLOAD_STARTED = "com.android.settings.DOWNLOAD_STARTED";
    public static final String ACTION_DOWNLOAD_SUCCESS = "com.android.settings.DOWNLOAD_SUCCESS";
    public static final String ACTION_DOWNLOAD_ERROR = "com.android.settings.DOWNLOAD_ERROR";
    public static final String ACTION_DOWNLOAD_PROGRESS = "com.android.settings.DOWNLOAD_PROGRESS";

    // Broadcast extras
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";
    public static final String EXTRA_PROGRESS = "progress";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final IBinder binder = new LocalBinder();
    private boolean isDownloading = false;
    private String currentDownloadId;

    public class LocalBinder extends Binder {
        public ForegroundConfigDownloadService getService() {
            return ForegroundConfigDownloadService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String configUrl = intent.getStringExtra(EXTRA_CONFIG_URL);
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);

            String productModel = SystemPropertiesHelper.getProductModel().toLowerCase();
            //find the first occurrence of the update in configUrl
            int updateIndex = configUrl.indexOf("update");
            String newConfigUrl = null;
            if (updateIndex != -1) {
                newConfigUrl = configUrl.substring(0, updateIndex + 6) + "/" + productModel  + configUrl.substring(updateIndex + 6);
            }
            Log.i(TAG, "newConfigUrl: " + newConfigUrl);

            if (newConfigUrl != null && !isDownloading) {
                startDownload(newConfigUrl, downloadId);
            }
        }

        // Return START_STICKY to restart service if killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Config Download Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows progress of config file downloads");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading Config")
                .setContentText("Downloading configuration file...")
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotification(String title, String content, int progress) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        if (progress >= 0) {
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void showFinalNotification(String title, String content, boolean isSuccess) {
        // Cancel the ongoing notification first
        stopForeground(true);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(isSuccess ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            // Use a different notification ID for the final notification
            manager.notify(NOTIFICATION_ID + 1, builder.build());
        }
    }

    private void startDownload(String configUrl, String downloadId) {
        if (isDownloading) {
            Log.w(TAG, "Download already in progress");
            return;
        }

        isDownloading = true;
        currentDownloadId = downloadId;

        startForegroundService();
        sendBroadcast(ACTION_DOWNLOAD_STARTED, downloadId, null, 0);

        executorService.execute(() -> {
            try {
                Log.i(TAG, "Starting config download from: " + configUrl);
                updateNotification("Downloading Config", "Connecting to server...", -1);

                String configContent = downloadFromUrl(configUrl);
                String filename = generateFilename();

                updateNotification("Downloading Config", "Saving file...", 90);
                saveConfigFile(filename, configContent);

                // Validate the downloaded config
                UpdateConfig config = UpdateConfig.fromJson(configContent);

                sendBroadcast(ACTION_DOWNLOAD_SUCCESS, downloadId, filename, 100);

                Log.i(TAG, "Config download completed successfully: " + filename);

                // Show final success notification that can be dismissed
                showFinalNotification("Download Complete", "Config downloaded successfully", true);

            } catch (IOException e) {
                Log.e(TAG, "Network error downloading config", e);
                String errorMessage = "Please check your network connection and try again.";
                sendBroadcast(ACTION_DOWNLOAD_ERROR, downloadId, errorMessage, 0);

                // Show final error notification that can be dismissed
                showFinalNotification("Download Failed", errorMessage, false);

            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON in downloaded config", e);
                String errorMessage = "No new version found.";
                sendBroadcast(ACTION_DOWNLOAD_ERROR, downloadId, errorMessage, 0);

                // Show final error notification that can be dismissed
                showFinalNotification("Download Failed", errorMessage, false);

            } catch (Exception e) {
                Log.e(TAG, "Unexpected error downloading config", e);
                String errorMessage = "No new version found.";
                sendBroadcast(ACTION_DOWNLOAD_ERROR, downloadId, errorMessage, 0);

                // Show final error notification that can be dismissed
                showFinalNotification("Download Failed", errorMessage, false);

            } finally {
                isDownloading = false;
                // Keep service running for potential future downloads
                // stopForeground(true);
                // stopSelf();
            }
        });
    }

    private String getErrorMessage(IOException e) {
        String errorMessage = e.getMessage();
        if (errorMessage.contains("HTML")) {
            return "The URL returned HTML instead of JSON. Please check the URL.";
        } else if (errorMessage.contains("HTTP error")) {
            return "Server error: " + errorMessage;
        } else {
            return "Network error: " + errorMessage;
        }
    }

    private void sendBroadcast(String action, String downloadId, String extra, int progress) {
        Intent broadcast = new Intent(action);
        broadcast.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        if (extra != null) {
            if (action.equals(ACTION_DOWNLOAD_SUCCESS)) {
                broadcast.putExtra(EXTRA_FILENAME, extra);
            } else if (action.equals(ACTION_DOWNLOAD_ERROR)) {
                broadcast.putExtra(EXTRA_ERROR_MESSAGE, extra);
            }
        }
        if (action.equals(ACTION_DOWNLOAD_PROGRESS)) {
            broadcast.putExtra(EXTRA_PROGRESS, progress);
        }
        sendBroadcast(broadcast);
    }

    private String downloadFromUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "SystemUpdaterSample/1.0");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorMessage = "HTTP error: " + responseCode;
                try {
                    InputStream errorStream = connection.getErrorStream();
                    if (errorStream != null) {
                        BufferedReader errorReader = new BufferedReader(
                                new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                        StringBuilder errorContent = new StringBuilder();
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null && errorContent.length() < 200) {
                            errorContent.append(errorLine).append("\n");
                        }
                        if (errorContent.length() > 0) {
                            errorMessage += " - " + errorContent.toString().trim();
                        }
                    }
                } catch (Exception e) {
                    // Ignore error reading error stream
                }
                throw new IOException(errorMessage);
            }

            // Check content length
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_FILE_SIZE) {
                throw new IOException("File too large: " + contentLength + " bytes");
            }

            InputStream inputStream = connection.getInputStream();
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            String line;
            int totalRead = 0;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
                totalRead += line.length();

                // Check accumulated size
                if (content.length() > MAX_FILE_SIZE) {
                    throw new IOException("File too large during download");
                }

                // Update progress if we know content length
                if (contentLength > 0) {
                    int progress = (int) ((totalRead * 80) / contentLength); // 80% for download
                    updateNotification("Downloading Config", "Downloading... " + progress + "%", progress);
                    sendBroadcast(ACTION_DOWNLOAD_PROGRESS, currentDownloadId, null, progress);
                }
            }

            String downloadedContent = content.toString().trim();

            // Check if content looks like HTML instead of JSON
            if (downloadedContent.startsWith("<!DOCTYPE") ||
                downloadedContent.startsWith("<html") ||
                downloadedContent.startsWith("<HTML")) {
                throw new IOException("Downloaded content appears to be HTML, not JSON. The URL may be incorrect or the server returned an error page.");
            }

            return downloadedContent;

        } finally {
            connection.disconnect();
        }
    }

    private void saveConfigFile(String filename, String content) throws IOException {
        File configsDir = new File(UpdateConfigs.getConfigsRoot(this));
        if (!configsDir.exists()) {
            if (!configsDir.mkdirs()) {
                throw new IOException("Failed to create configs directory");
            }
        }

        File configFile = new File(configsDir, filename);
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }

        Log.i(TAG, "Config file saved: " + configFile.getAbsolutePath());
    }

    private String generateFilename() {
        return "ota_config.json";
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public String getCurrentDownloadId() {
        return currentDownloadId;
    }
}
