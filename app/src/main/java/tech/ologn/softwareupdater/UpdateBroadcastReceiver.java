package tech.ologn.softwareupdater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import tech.ologn.softwareupdater.services.ForegroundConfigDownloadService;
import tech.ologn.softwareupdater.services.ForegroundPrepareUpdateService;

/**
 * Broadcast receiver to handle updates from foreground services.
 * This allows the UI to stay updated even when the app is not in foreground.
 */
public class UpdateBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateBroadcastReceiver";

    public interface UpdateListener {
        void onDownloadStarted(String downloadId);
        void onDownloadSuccess(String downloadId, String filename);
        void onDownloadError(String downloadId, String errorMessage);
        void onDownloadProgress(String downloadId, int progress);

        void onPrepareStarted(String updateId);
        void onPrepareSuccess(String updateId);
        void onPrepareError(String updateId, String errorMessage);
        void onPrepareProgress(String updateId, int progress);
    }

    private final List<UpdateListener> mListeners = new CopyOnWriteArrayList<>();

    public UpdateBroadcastReceiver(UpdateListener listener) {
        if (listener != null) {
            this.mListeners.add(listener);
        }
    }

    public UpdateBroadcastReceiver() {
        // Empty constructor for fragments to add listeners later
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }

        Log.d(TAG, "Received broadcast: " + action);

        switch (action) {
            // Config Download Service broadcasts
            case ForegroundConfigDownloadService.ACTION_DOWNLOAD_STARTED:
                handleDownloadStarted(intent);
                break;
            case ForegroundConfigDownloadService.ACTION_DOWNLOAD_SUCCESS:
                handleDownloadSuccess(intent);
                break;
            case ForegroundConfigDownloadService.ACTION_DOWNLOAD_ERROR:
                handleDownloadError(intent);
                break;
            case ForegroundConfigDownloadService.ACTION_DOWNLOAD_PROGRESS:
                handleDownloadProgress(intent);
                break;

            // Prepare Update Service broadcasts
            case ForegroundPrepareUpdateService.ACTION_PREPARE_STARTED:
                handlePrepareStarted(intent);
                break;
            case ForegroundPrepareUpdateService.ACTION_PREPARE_SUCCESS:
                handlePrepareSuccess(intent);
                break;
            case ForegroundPrepareUpdateService.ACTION_PREPARE_ERROR:
                handlePrepareError(intent);
                break;
            case ForegroundPrepareUpdateService.ACTION_PREPARE_PROGRESS:
                handlePrepareProgress(intent);
                break;
        }
    }

    private void handleDownloadStarted(Intent intent) {
        String downloadId = intent.getStringExtra(ForegroundConfigDownloadService.EXTRA_DOWNLOAD_ID);
        Log.d(TAG, "Download started: " + downloadId);
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onDownloadStarted(downloadId);
            }
        }
    }

    private void handleDownloadSuccess(Intent intent) {
        String downloadId = intent.getStringExtra(ForegroundConfigDownloadService.EXTRA_DOWNLOAD_ID);
        String filename = intent.getStringExtra(ForegroundConfigDownloadService.EXTRA_FILENAME);
        Log.d(TAG, "Download success: " + downloadId + ", filename: " + filename);
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onDownloadSuccess(downloadId, filename);
            }
        }
    }

    private void handleDownloadError(Intent intent) {
        String downloadId = intent.getStringExtra(ForegroundConfigDownloadService.EXTRA_DOWNLOAD_ID);
        String errorMessage = intent.getStringExtra(ForegroundConfigDownloadService.EXTRA_ERROR_MESSAGE);
        Log.e(TAG, "Download error: " + downloadId + ", error: " + errorMessage);
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onDownloadError(downloadId, errorMessage);
            }
        }
    }

    private void handleDownloadProgress(Intent intent) {
        String downloadId = intent.getStringExtra(ForegroundConfigDownloadService.EXTRA_DOWNLOAD_ID);
        int progress = intent.getIntExtra(ForegroundConfigDownloadService.EXTRA_PROGRESS, 0);
        Log.d(TAG, "Download progress: " + downloadId + ", progress: " + progress + "%");
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onDownloadProgress(downloadId, progress);
            }
        }
    }

    private void handlePrepareStarted(Intent intent) {
        String updateId = intent.getStringExtra(ForegroundPrepareUpdateService.EXTRA_UPDATE_ID);
        Log.d(TAG, "Prepare started: " + updateId);
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onPrepareStarted(updateId);
            }
        }
    }

    private void handlePrepareSuccess(Intent intent) {
        String updateId = intent.getStringExtra(ForegroundPrepareUpdateService.EXTRA_UPDATE_ID);
        Log.d(TAG, "Prepare success: " + updateId);
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onPrepareSuccess(updateId);
            }
        }
    }

    private void handlePrepareError(Intent intent) {
        String updateId = intent.getStringExtra(ForegroundPrepareUpdateService.EXTRA_UPDATE_ID);
        String errorMessage = intent.getStringExtra(ForegroundPrepareUpdateService.EXTRA_ERROR_MESSAGE);
        Log.e(TAG, "Prepare error: " + updateId + ", error: " + errorMessage);
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onPrepareError(updateId, errorMessage);
            }
        }
    }

    private void handlePrepareProgress(Intent intent) {
        String updateId = intent.getStringExtra(ForegroundPrepareUpdateService.EXTRA_UPDATE_ID);
        int progress = intent.getIntExtra(ForegroundPrepareUpdateService.EXTRA_PROGRESS, 0);
        Log.d(TAG, "Prepare progress: " + updateId + ", progress: " + progress + "%");
        for (UpdateListener listener : mListeners) {
            if (listener != null) {
                listener.onPrepareProgress(updateId, progress);
            }
        }
    }

    /**
     * Add a listener to receive broadcast updates.
     * @param listener The listener to add
     */
    public void addListener(UpdateListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Remove a listener from receiving broadcast updates.
     * @param listener The listener to remove
     */
    public void removeListener(UpdateListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Set a single listener (replaces all existing listeners).
     * @param listener The listener to set
     * @deprecated Use addListener() instead to support multiple listeners
     */
    @Deprecated
    public void setListener(UpdateListener listener) {
        mListeners.clear();
        if (listener != null) {
            mListeners.add(listener);
        }
    }
}
