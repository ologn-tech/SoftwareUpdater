package tech.ologn.softwareupdater;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages persistent storage of update states to survive app restarts.
 */
public class UpdateStateManager {

    private static final String TAG = "UpdateStateManager";
    private static final String PREFS_NAME = "update_state_prefs";
    
    // Keys for different state types
    private static final String KEY_ACTIVE_DOWNLOADS = "active_downloads";
    private static final String KEY_ACTIVE_UPDATES = "active_updates";
    private static final String KEY_LAST_UPDATE_CONFIG = "last_update_config";
    private static final String KEY_UPDATE_PROGRESS = "update_progress";

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public UpdateStateManager(Context context) {
        this.mContext = context;
        this.mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Represents an active download operation
     */
    public static class ActiveDownload {
        public String downloadId;
        public String configUrl;
        public long startTime;
        public int progress;
        public String status; // "downloading", "completed", "error"
        public String errorMessage;

        public ActiveDownload() {}

        public ActiveDownload(String downloadId, String configUrl) {
            this.downloadId = downloadId;
            this.configUrl = configUrl;
            this.startTime = System.currentTimeMillis();
            this.progress = 0;
            this.status = "downloading";
        }
    }

    /**
     * Represents an active update operation
     */
    public static class ActiveUpdate {
        public String updateId;
        public String configName;
        public long startTime;
        public int progress;
        public String status; // "preparing", "applying", "completed", "error"
        public String errorMessage;

        public ActiveUpdate() {}

        public ActiveUpdate(String updateId, String configName) {
            this.updateId = updateId;
            this.configName = configName;
            this.startTime = System.currentTimeMillis();
            this.progress = 0;
            this.status = "preparing";
        }
    }

    /**
     * Represents overall update progress
     */
    public static class UpdateProgress {
        public String currentOperation; // "download", "prepare", "apply"
        public int overallProgress;
        public String currentStep;
        public long lastUpdateTime;

        public UpdateProgress() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    // Active Downloads Management
    public void addActiveDownload(ActiveDownload download) {
        List<ActiveDownload> downloads = getActiveDownloads();
        downloads.add(download);
        saveActiveDownloads(downloads);
        Log.d(TAG, "Added active download: " + download.downloadId);
    }

    public void updateDownloadProgress(String downloadId, int progress) {
        List<ActiveDownload> downloads = getActiveDownloads();
        for (ActiveDownload download : downloads) {
            if (download.downloadId.equals(downloadId)) {
                download.progress = progress;
                break;
            }
        }
        saveActiveDownloads(downloads);
    }

    public void updateDownloadStatus(String downloadId, String status, String errorMessage) {
        List<ActiveDownload> downloads = getActiveDownloads();
        for (ActiveDownload download : downloads) {
            if (download.downloadId.equals(downloadId)) {
                download.status = status;
                if (errorMessage != null) {
                    download.errorMessage = errorMessage;
                }
                break;
            }
        }
        saveActiveDownloads(downloads);
        Log.d(TAG, "Updated download status: " + downloadId + " -> " + status);
    }

    public void removeAllDownloads() {
        mPrefs.edit().remove(KEY_ACTIVE_DOWNLOADS).apply();
        Log.d(TAG, "Removed all active downloads");
    }

    public List<ActiveDownload> getActiveDownloads() {
        String json = mPrefs.getString(KEY_ACTIVE_DOWNLOADS, "[]");
        List<ActiveDownload> downloads = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                ActiveDownload download = new ActiveDownload();
                download.downloadId = jsonObject.optString("downloadId", "");
                download.configUrl = jsonObject.optString("configUrl", "");
                download.startTime = jsonObject.optLong("startTime", 0);
                download.progress = jsonObject.optInt("progress", 0);
                download.status = jsonObject.optString("status", "downloading");
                download.errorMessage = jsonObject.optString("errorMessage", null);
                downloads.add(download);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse active downloads", e);
        }
        return downloads;
    }

    private void saveActiveDownloads(List<ActiveDownload> downloads) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ActiveDownload download : downloads) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("downloadId", download.downloadId);
                jsonObject.put("configUrl", download.configUrl);
                jsonObject.put("startTime", download.startTime);
                jsonObject.put("progress", download.progress);
                jsonObject.put("status", download.status);
                if (download.errorMessage != null) {
                    jsonObject.put("errorMessage", download.errorMessage);
                }
                jsonArray.put(jsonObject);
            }
            mPrefs.edit().putString(KEY_ACTIVE_DOWNLOADS, jsonArray.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save active downloads", e);
        }
    }

    // Active Updates Management
    public void addActiveUpdate(ActiveUpdate update) {
        List<ActiveUpdate> updates = getActiveUpdates();
        updates.add(update);
        saveActiveUpdates(updates);
        Log.d(TAG, "Added active update: " + update.updateId);
    }

    public void updateUpdateProgress(String updateId, int progress) {
        List<ActiveUpdate> updates = getActiveUpdates();
        for (ActiveUpdate update : updates) {
            if (update.updateId.equals(updateId)) {
                update.progress = progress;
                break;
            }
        }
        saveActiveUpdates(updates);
    }

    public void updateUpdateStatus(String updateId, String status, String errorMessage) {
        List<ActiveUpdate> updates = getActiveUpdates();
        for (ActiveUpdate update : updates) {
            if (update.updateId.equals(updateId)) {
                update.status = status;
                if (errorMessage != null) {
                    update.errorMessage = errorMessage;
                }
                break;
            }
        }
        saveActiveUpdates(updates);
        Log.d(TAG, "Updated update status: " + updateId + " -> " + status);
    }

    public void removeActiveUpdate(String updateId) {
        List<ActiveUpdate> updates = getActiveUpdates();
        updates.removeIf(update -> update.updateId.equals(updateId));
        saveActiveUpdates(updates);
        Log.d(TAG, "Removed active update: " + updateId);
    }

    public List<ActiveUpdate> getActiveUpdates() {
        String json = mPrefs.getString(KEY_ACTIVE_UPDATES, "[]");
        List<ActiveUpdate> updates = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                ActiveUpdate update = new ActiveUpdate();
                update.updateId = jsonObject.optString("updateId", "");
                update.configName = jsonObject.optString("configName", "");
                update.startTime = jsonObject.optLong("startTime", 0);
                update.progress = jsonObject.optInt("progress", 0);
                update.status = jsonObject.optString("status", "preparing");
                update.errorMessage = jsonObject.optString("errorMessage", null);
                updates.add(update);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse active updates", e);
        }
        return updates;
    }

    private void saveActiveUpdates(List<ActiveUpdate> updates) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ActiveUpdate update : updates) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("updateId", update.updateId);
                jsonObject.put("configName", update.configName);
                jsonObject.put("startTime", update.startTime);
                jsonObject.put("progress", update.progress);
                jsonObject.put("status", update.status);
                if (update.errorMessage != null) {
                    jsonObject.put("errorMessage", update.errorMessage);
                }
                jsonArray.put(jsonObject);
            }
            mPrefs.edit().putString(KEY_ACTIVE_UPDATES, jsonArray.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save active updates", e);
        }
    }

    // Update Progress Management
    public void setUpdateProgress(UpdateProgress progress) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("currentOperation", progress.currentOperation);
            jsonObject.put("overallProgress", progress.overallProgress);
            jsonObject.put("currentStep", progress.currentStep);
            jsonObject.put("lastUpdateTime", progress.lastUpdateTime);
            mPrefs.edit().putString(KEY_UPDATE_PROGRESS, jsonObject.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save update progress", e);
        }
    }

    public UpdateProgress getUpdateProgress() {
        String json = mPrefs.getString(KEY_UPDATE_PROGRESS, null);
        if (json != null) {
            try {
                JSONObject jsonObject = new JSONObject(json);
                UpdateProgress progress = new UpdateProgress();
                progress.currentOperation = jsonObject.optString("currentOperation", "");
                progress.overallProgress = jsonObject.optInt("overallProgress", 0);
                progress.currentStep = jsonObject.optString("currentStep", "");
                progress.lastUpdateTime = jsonObject.optLong("lastUpdateTime", System.currentTimeMillis());
                return progress;
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse update progress", e);
            }
        }
        return new UpdateProgress();
    }

    // Last Update Config Management
    public void setLastUpdateConfig(UpdateConfig config) {
        // For UpdateConfig, we'll store just the essential info as JSON
        // Since UpdateConfig is complex, we'll store a simplified version
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", config.getName());
            jsonObject.put("url", config.getUrl());
            jsonObject.put("installType", config.getInstallType());
            mPrefs.edit().putString(KEY_LAST_UPDATE_CONFIG, jsonObject.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save last update config", e);
        }
    }

    public UpdateConfig getLastUpdateConfig() {
        // Note: This is a simplified version. In a real implementation,
        // you might want to store more config data or use a different approach
        String json = mPrefs.getString(KEY_LAST_UPDATE_CONFIG, null);
        if (json != null) {
            try {
                JSONObject jsonObject = new JSONObject(json);
                // Return null for now since we can't fully reconstruct UpdateConfig
                // In a real implementation, you'd need to store more data or use a different approach
                Log.d(TAG, "Last update config found: " + jsonObject.optString("name", "unknown"));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse last update config", e);
            }
        }
        return null;
    }

    // Utility methods
    public boolean hasActiveOperations() {
        List<ActiveDownload> downloads = getActiveDownloads();
        List<ActiveUpdate> updates = getActiveUpdates();
        
        boolean hasActiveDownloads = downloads.stream()
                .anyMatch(d -> "downloading".equals(d.status));
        boolean hasActiveUpdates = updates.stream()
                .anyMatch(u -> "preparing".equals(u.status) || "applying".equals(u.status));
        
        return hasActiveDownloads || hasActiveUpdates;
    }

    public void clearAllStates() {
        mPrefs.edit()
                .remove(KEY_ACTIVE_DOWNLOADS)
                .remove(KEY_ACTIVE_UPDATES)
                .remove(KEY_UPDATE_PROGRESS)
                .remove(KEY_LAST_UPDATE_CONFIG)
                .apply();
        Log.d(TAG, "Cleared all update states");
    }

    public void cleanupCompletedOperations() {
        // Remove completed downloads older than 1 hour
        List<ActiveDownload> downloads = getActiveDownloads();
        long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
        downloads.removeIf(download -> 
                ("completed".equals(download.status) || "error".equals(download.status)) 
                && download.startTime < oneHourAgo);
        saveActiveDownloads(downloads);

        // Remove completed updates older than 1 hour
        List<ActiveUpdate> updates = getActiveUpdates();
        updates.removeIf(update -> 
                ("completed".equals(update.status) || "error".equals(update.status)) 
                && update.startTime < oneHourAgo);
        saveActiveUpdates(updates);

        Log.d(TAG, "Cleaned up completed operations");
    }

    public String generateDownloadId() {
        return "download_" + System.currentTimeMillis();
    }

    public String generateUpdateId() {
        return "update_" + System.currentTimeMillis();
    }
}
