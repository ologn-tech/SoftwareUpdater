package tech.ologn.softwareupdater.services;

import static tech.ologn.softwareupdater.utils.PackageFiles.COMPATIBILITY_ZIP_FILE_NAME;
import static tech.ologn.softwareupdater.utils.PackageFiles.OTA_PACKAGE_DIR;
import static tech.ologn.softwareupdater.utils.PackageFiles.PAYLOAD_BINARY_FILE_NAME;
import static tech.ologn.softwareupdater.utils.PackageFiles.PAYLOAD_PROPERTIES_FILE_NAME;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RecoverySystem;
import android.os.ResultReceiver;
import android.os.UpdateEngine;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import tech.ologn.softwareupdater.MainActivity;
import tech.ologn.softwareupdater.PayloadSpec;
import tech.ologn.softwareupdater.R;
import tech.ologn.softwareupdater.UpdateConfig;
import tech.ologn.softwareupdater.utils.FileDownloader;
import tech.ologn.softwareupdater.utils.PackageFiles;
import tech.ologn.softwareupdater.utils.PayloadSpecs;
import tech.ologn.softwareupdater.utils.UpdateConfigs;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Optional;
import java.lang.Runtime;
import java.lang.Process;

/**
 * Foreground service for preparing updates that continues running even when app is destroyed.
 */
public class ForegroundPrepareUpdateService extends Service {

    private static final String TAG = "ForegroundPrepareUpdate";
    private static final String CHANNEL_ID = "prepare_update_channel";
    private static final int NOTIFICATION_ID = 1002;

    // Intent extras
    public static final String EXTRA_PARAM_CONFIG = "config";
    public static final String EXTRA_PARAM_RESULT_RECEIVER = "result-receiver";
    public static final String EXTRA_UPDATE_ID = "update_id";

    // Broadcast actions
    public static final String ACTION_PREPARE_STARTED = "com.android.settings.PREPARE_STARTED";
    public static final String ACTION_PREPARE_SUCCESS = "com.android.settings.PREPARE_SUCCESS";
    public static final String ACTION_PREPARE_ERROR = "com.android.settings.PREPARE_ERROR";
    public static final String ACTION_PREPARE_PROGRESS = "com.android.settings.PREPARE_PROGRESS";

    // Broadcast extras
    public static final String EXTRA_ERROR_MESSAGE = "error_message";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_PAYLOAD_SPEC = "payload_spec";

    /**
     * UpdateResultCallback result codes.
     */
    public static final int RESULT_CODE_SUCCESS = 0;
    public static final int RESULT_CODE_ERROR = 1;

    /**
     * This interface is used to send results from ForegroundPrepareUpdateService to
     * the calling activity.
     */
    public interface UpdateResultCallback {
        /**
         * Invoked when files are downloaded and payload spec is constructed.
         *
         * @param resultCode  result code, values are defined in ForegroundPrepareUpdateService
         * @param payloadSpec prepared payload spec for streaming update
         */
        void onReceiveResult(int resultCode, PayloadSpec payloadSpec);
    }

    /**
     * The files that should be downloaded before streaming.
     */
    private static final ImmutableSet<String> PRE_STREAMING_FILES_SET =
            ImmutableSet.of(
                    PackageFiles.CARE_MAP_FILE_NAME,
                    PackageFiles.COMPATIBILITY_ZIP_FILE_NAME,
                    PackageFiles.METADATA_FILE_NAME,
                    PackageFiles.PAYLOAD_PROPERTIES_FILE_NAME
            );

    private final PayloadSpecs mPayloadSpecs = new PayloadSpecs();
    private final UpdateEngine mUpdateEngine = new UpdateEngine();
    private final IBinder binder = new LocalBinder();
    private boolean isPreparing = false;
    private String currentUpdateId;

    public class LocalBinder extends Binder {
        public ForegroundPrepareUpdateService getService() {
            return ForegroundPrepareUpdateService.this;
        }
    }

    /**
     * Starts ForegroundPrepareUpdateService.
     *
     * @param context        application context
     * @param config         update config
     * @param resultCallback callback that will be called when the update is ready to be installed
     */
    public static void startService(Context context,
            UpdateConfig config,
            Handler handler,
            UpdateResultCallback resultCallback,
            String updateId) {
        Log.d(TAG, "Starting ForegroundPrepareUpdateService");
        ResultReceiver receiver = new CallbackResultReceiver(handler, resultCallback);
        Intent intent = new Intent(context, ForegroundPrepareUpdateService.class);
        intent.putExtra(EXTRA_PARAM_CONFIG, config);
        intent.putExtra(EXTRA_PARAM_RESULT_RECEIVER, receiver);
        intent.putExtra(EXTRA_UPDATE_ID, updateId);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            UpdateConfig config = intent.getParcelableExtra(EXTRA_PARAM_CONFIG);
            ResultReceiver resultReceiver = intent.getParcelableExtra(EXTRA_PARAM_RESULT_RECEIVER);
            String updateId = intent.getStringExtra(EXTRA_UPDATE_ID);
            
            if (config != null && !isPreparing) {
                startPrepareUpdate(config, resultReceiver, updateId);
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
        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Prepare Update Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows progress of update preparation");
            
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
                .setContentTitle("Preparing Update")
                .setContentText("Preparing update files...")
                .setSmallIcon(R.drawable.ic_system_update)
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
                .setSmallIcon(R.drawable.ic_system_update)
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

    private void startPrepareUpdate(UpdateConfig config, ResultReceiver resultReceiver, String updateId) {
        if (isPreparing) {
            Log.w(TAG, "Update preparation already in progress");
            return;
        }

        isPreparing = true;
        currentUpdateId = updateId;
        
        startForegroundService();
        sendBroadcast(ACTION_PREPARE_STARTED, updateId, null, 0);

        new Thread(() -> {
            try {
                Log.d(TAG, "On handle intent is called");
                PayloadSpec spec = execute(config);
                
                sendBroadcast(ACTION_PREPARE_SUCCESS, updateId, null, 100);
                
                if (resultReceiver != null) {
                    resultReceiver.send(RESULT_CODE_SUCCESS, CallbackResultReceiver.createBundle(spec));
                }
                
                // Show final success notification that can be dismissed
                showFinalNotification("Update Ready", "Update preparation completed successfully", true);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to prepare streaming update", e);
                String errorMessage = "Update preparation failed: " + e.getMessage();
                sendBroadcast(ACTION_PREPARE_ERROR, updateId, errorMessage, 0);
                
                if (resultReceiver != null) {
                    resultReceiver.send(RESULT_CODE_ERROR, null);
                }
                
                // Show final error notification that can be dismissed
                showFinalNotification("Update Failed", errorMessage, false);
                
            } finally {
                isPreparing = false;
                // Keep service running for potential future updates
                // stopForeground(true);
                // stopSelf();
            }
        }).start();
    }

    private void sendBroadcast(String action, String updateId, String extra, int progress) {
        Intent broadcast = new Intent(action);
        broadcast.putExtra(EXTRA_UPDATE_ID, updateId);
        if (extra != null) {
            if (action.equals(ACTION_PREPARE_ERROR)) {
                broadcast.putExtra(EXTRA_ERROR_MESSAGE, extra);
            }
        }
        if (action.equals(ACTION_PREPARE_PROGRESS)) {
            broadcast.putExtra(EXTRA_PROGRESS, progress);
        }
        sendBroadcast(broadcast);
    }

    /**
     * 1. Downloads files for streaming updates.
     * 2. Makes sure required files are present.
     * 3. Checks OTA package compatibility with the device.
     * 4. Constructs {@link PayloadSpec} for streaming update.
     */
    private PayloadSpec execute(UpdateConfig config)
            throws IOException, PreparationFailedException {

        updateNotification("Preparing Update", "Verifying payload metadata...", 10);
        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 10);

        if (config.getAbConfig().getVerifyPayloadMetadata()) {
            Log.i(TAG, "Verifying payload metadata with UpdateEngine.");
            if (!verifyPayloadMetadata(config)) {
                throw new PreparationFailedException("Payload metadata is not compatible");
            }
        }

        updateNotification("Preparing Update", "Checking install type...", 20);
        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 20);

        if (config.getInstallType() == UpdateConfig.AB_INSTALL_TYPE_NON_STREAMING) {
            try {
                File updatePackageFile = config.getUpdatePackageFile();
                return mPayloadSpecs.forNonStreaming(updatePackageFile);
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    if (e.getMessage().contains("http")) {
                        Log.i(TAG, "Downloading update package from http to " + OTA_PACKAGE_DIR);
                        updateNotification("Preparing Update", "Downloading update package...", 30);
                        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 30);
                        
                        File updateOTA = Paths.get(OTA_PACKAGE_DIR, "update.zip").toFile();

                        FileDownloader downloader = new FileDownloader(
                                config.getUrl(),
                                0,
                                -1,
                                updateOTA);

                        downloader.download();

                        updateNotification("Preparing Update", "Setting permissions...", 70);
                        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 70);

                        try {
                            Process process = Runtime.getRuntime().exec(
                                new String[]{"chmod", "660", updateOTA.getAbsolutePath()});
                            int result = process.waitFor();
                            if (result != 0) {
                                Log.w("FileDownloader", "chmod failed with code " + result);
                            }
                        } catch (Exception errorChmod) {
                            Log.e("FileDownloader", "chmod failed", errorChmod);
                        }

                        updateNotification("Preparing Update", "Update package downloaded", 80);
                        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 80);

                        Log.i(TAG, "Downloaded update package from http to " + updateOTA.getAbsolutePath());
                        return mPayloadSpecs.forNonStreaming(updateOTA);
                    }
                }
                throw new PreparationFailedException("Failed to download update package");
            }
        }

        updateNotification("Preparing Update", "Downloading pre-streaming files...", 40);
        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 40);
        downloadPreStreamingFiles(config, OTA_PACKAGE_DIR);

        updateNotification("Preparing Update", "Processing payload binary...", 70);
        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 70);

        Optional<UpdateConfig.PackageFile> payloadBinary =
                UpdateConfigs.getPropertyFile(PAYLOAD_BINARY_FILE_NAME, config);

        if (!payloadBinary.isPresent()) {
            throw new PreparationFailedException(
                    "Failed to find " + PAYLOAD_BINARY_FILE_NAME + " in config");
        }

        if (!UpdateConfigs.getPropertyFile(PAYLOAD_PROPERTIES_FILE_NAME, config).isPresent()
                || !Paths.get(OTA_PACKAGE_DIR, PAYLOAD_PROPERTIES_FILE_NAME).toFile().exists()) {
            throw new IOException(PAYLOAD_PROPERTIES_FILE_NAME + " not found");
        }

        updateNotification("Preparing Update", "Verifying compatibility...", 80);
        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 80);

        File compatibilityFile = Paths.get(OTA_PACKAGE_DIR, COMPATIBILITY_ZIP_FILE_NAME).toFile();
        if (compatibilityFile.isFile()) {
            Log.i(TAG, "Verifying OTA package for compatibility with the device");
            if (!verifyPackageCompatibility(compatibilityFile)) {
                throw new PreparationFailedException(
                        "OTA package is not compatible with this device");
            }
        }

        updateNotification("Preparing Update", "Finalizing payload spec...", 90);
        sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 90);

        return mPayloadSpecs.forStreaming(config.getUrl(),
                payloadBinary.get().getOffset(),
                payloadBinary.get().getSize(),
                Paths.get(OTA_PACKAGE_DIR, PAYLOAD_PROPERTIES_FILE_NAME).toFile());
    }

    /**
     * Downloads only payload_metadata.bin and verifies with
     * {@link UpdateEngine#verifyPayloadMetadata}.
     */
    private boolean verifyPayloadMetadata(UpdateConfig config) {
        Optional<UpdateConfig.PackageFile> metadataPackageFile =
                Arrays.stream(config.getAbConfig().getPropertyFiles())
                        .filter(p -> p.getFilename().equals(
                                PackageFiles.PAYLOAD_METADATA_FILE_NAME))
                        .findFirst();
        if (!metadataPackageFile.isPresent()) {
            Log.w(TAG, String.format("ab_config.property_files doesn't contain %s",
                    PackageFiles.PAYLOAD_METADATA_FILE_NAME));
            return true;
        }
        Path metadataPath = Paths.get(OTA_PACKAGE_DIR, PackageFiles.PAYLOAD_METADATA_FILE_NAME);
        try {
            Files.deleteIfExists(metadataPath);
            FileDownloader d = new FileDownloader(
                    config.getUrl(),
                    metadataPackageFile.get().getOffset(),
                    metadataPackageFile.get().getSize(),
                    metadataPath.toFile());
            d.download();
        } catch (IOException e) {
            Log.w(TAG, String.format("Downloading %s from %s failed",
                    PackageFiles.PAYLOAD_METADATA_FILE_NAME,
                    config.getUrl()), e);
            return true;
        }
        try {
            return mUpdateEngine.verifyPayloadMetadata(metadataPath.toAbsolutePath().toString());
        } catch (Exception e) {
            Log.w(TAG, "UpdateEngine#verifyPayloadMetadata failed", e);
            return true;
        }
    }

    /**
     * Downloads files defined in {@link UpdateConfig#getAbConfig()}
     * and exists in {@code PRE_STREAMING_FILES_SET}, and put them
     * in directory {@code dir}.
     */
    private void downloadPreStreamingFiles(UpdateConfig config, String dir)
            throws IOException {
        Log.d(TAG, "Deleting existing files from " + dir);
        for (String file : PRE_STREAMING_FILES_SET) {
            Files.deleteIfExists(Paths.get(OTA_PACKAGE_DIR, file));
        }
        Log.d(TAG, "Downloading files to " + dir);
        
        int totalFiles = config.getAbConfig().getPropertyFiles().length;
        int currentFile = 0;
        
        for (UpdateConfig.PackageFile file : config.getAbConfig().getPropertyFiles()) {
            if (PRE_STREAMING_FILES_SET.contains(file.getFilename())) {
                Log.d(TAG, "Downloading file " + file.getFilename());
                updateNotification("Preparing Update", "Downloading " + file.getFilename() + "...", 
                        40 + (currentFile * 30 / totalFiles));
                sendBroadcast(ACTION_PREPARE_PROGRESS, currentUpdateId, null, 
                        40 + (currentFile * 30 / totalFiles));
                
                FileDownloader downloader = new FileDownloader(
                        config.getUrl(),
                        file.getOffset(),
                        file.getSize(),
                        Paths.get(dir, file.getFilename()).toFile());
                downloader.download();
                currentFile++;
            }
        }
    }

    /**
     * @param file physical location of {@link PackageFiles#COMPATIBILITY_ZIP_FILE_NAME}
     * @return true if OTA package is compatible with this device
     */
    private boolean verifyPackageCompatibility(File file) {
        try {
            RecoverySystem.verifyPackage(file, null, null);
            return  true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to verify package compatibility", e);
            return false;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isPreparing() {
        return isPreparing;
    }

    public String getCurrentUpdateId() {
        return currentUpdateId;
    }

    /**
     * Used by ForegroundPrepareUpdateService to pass {@link PayloadSpec}
     * to {@link UpdateResultCallback#onReceiveResult}.
     */
    private static class CallbackResultReceiver extends ResultReceiver {

        static Bundle createBundle(PayloadSpec payloadSpec) {
            Bundle b = new Bundle();
            b.putSerializable(BUNDLE_PARAM_PAYLOAD_SPEC, payloadSpec);
            return b;
        }

        private static final String BUNDLE_PARAM_PAYLOAD_SPEC = "payload-spec";

        private UpdateResultCallback mUpdateResultCallback;

        CallbackResultReceiver(Handler handler, UpdateResultCallback updateResultCallback) {
            super(handler);
            this.mUpdateResultCallback = updateResultCallback;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            PayloadSpec payloadSpec = null;
            if (resultCode == RESULT_CODE_SUCCESS) {
                payloadSpec = (PayloadSpec) resultData.getSerializable(BUNDLE_PARAM_PAYLOAD_SPEC);
            }
            mUpdateResultCallback.onReceiveResult(resultCode, payloadSpec);
        }
    }

    private static class PreparationFailedException extends Exception {
        PreparationFailedException(String message) {
            super(message);
        }
    }
}
