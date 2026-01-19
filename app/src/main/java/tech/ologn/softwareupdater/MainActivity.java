package tech.ologn.softwareupdater;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UpdateEngine;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.List;

import tech.ologn.softwareupdater.services.ForegroundConfigDownloadService;
import tech.ologn.softwareupdater.services.ForegroundPrepareUpdateService;
import tech.ologn.softwareupdater.utils.DialogHelper;
import tech.ologn.softwareupdater.utils.SystemPropertiesHelper;
import tech.ologn.softwareupdater.utils.UpdateConfigs;
import tech.ologn.softwareupdater.utils.UpdateEngineErrorCodes;
import tech.ologn.softwareupdater.utils.UpdateEngineStatuses;

public class MainActivity extends AppCompatActivity implements ModeActionListener{

    public static final String ACTION_SOFTWARE_UPDATE_SETTINGS = "com.android.settings.action.SOFTWARE_UPDATE_SETTINGS";
    private static final String TAG = "SoftwareUpdateSettings";
    private static final String CONFIG_URL_INCREMENTAL = "https://s3.eu-central-1.amazonaws.com/arvicom.cartp.ota/update/ota_config_incremental.json";
    private static final String CONFIG_URL_FULL = "https://s3.eu-central-1.amazonaws.com/arvicom.cartp.ota/update/ota_config_full.json";
    // Mode constants
    private static final String PREF_NAME = "software_update_prefs";
    private static final String PREF_MODE = "software_update_mode";
    private static final String PREF_MODE_SWITCH = "software_update_mode_switch";

    private static final String MODE_EASY = "easy";
    private static final String MODE_ADVANCED = "advanced";

    private TextView mTextViewBuild;
    private Button mButtonReboot;
    private ProgressBar mProgressBar;
    private TextView mTextViewUpdaterState;
    private List<UpdateConfig> mConfigs;
    private UpdateStateManager mUpdateStateManager;
    public UpdateBroadcastReceiver mBroadcastReceiver;
    private SharedPreferences mSharedPreferences;
    private String mCurrentMode = MODE_EASY;

    private final UpdateManager mUpdateManager =
            new UpdateManager(new UpdateEngine(), new Handler());

    private boolean mIsApply = false;
    private boolean mIsNewVersion = false;
    private boolean mIsIncrementalUpdate = false;
    private boolean mHasTriedFullUpdate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mSharedPreferences == null) {
            mSharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        mCurrentMode = mSharedPreferences.getString(PREF_MODE, MODE_EASY);
        boolean mCurrentSwitch = mSharedPreferences.getBoolean(PREF_MODE_SWITCH, false);

        setContentView(R.layout.activity_main);

        Fragment fragment;
        if (MODE_EASY.equals(mCurrentMode)) {
            fragment = new EasyFragment();
        } else {
            fragment = new AdvanceFragment();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container_view,
                            fragment,
                            null)
                    .commit();
        }

        setupLayoutPreferences();
//        setupClickListeners();

        mUpdateStateManager = new UpdateStateManager(this);
        setupBroadcastReceiver();

        uiResetWidgets();
//        loadUpdateConfigs();

        mUpdateManager.setOnStateChangeCallback(this::onUpdaterStateChange);
        mUpdateManager.setOnEngineStatusUpdateCallback(this::onEngineStatusUpdate);
        mUpdateManager.setOnEngineCompleteCallback(this::onEnginePayloadApplicationComplete);
//        mUpdateManager.setOnProgressUpdateCallback(this::onProgressUpdate);
        mUpdateManager.setUpdateStateManager(mUpdateStateManager);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Binding to UpdateEngine invokes onStatusUpdate callback,
        // persisted updater state has to be loaded and prepared beforehand.
        mUpdateManager.bind();
    }

    @Override
    public void onPause() {
        super.onPause();
        mUpdateManager.unbind();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }
        // Only clear callbacks if no active operations are running
        if (mUpdateStateManager != null && !mUpdateStateManager.hasActiveOperations()) {
            mUpdateManager.setOnEngineStatusUpdateCallback(null);
            mUpdateManager.setOnProgressUpdateCallback(null);
            mUpdateManager.setOnEngineCompleteCallback(null);
        }

    }

    private void setupLayoutPreferences() {
        mTextViewBuild = findViewById(R.id.textViewBuild);
        mTextViewUpdaterState = findViewById(R.id.textViewUpdaterState);

    }

    /** resets ui */
    private void uiResetWidgets() {
        mTextViewBuild.setText(Build.DISPLAY);
    }

    private void uiStateIdle() {
        uiResetWidgets();
        if (!mIsNewVersion) {
            return;
        }
    }

    private void uiStateRunning() {
        uiResetWidgets();
        if (MODE_ADVANCED.equals(mCurrentMode)) {
            mProgressBar.setEnabled(true);
            mProgressBar.setVisibility(ProgressBar.VISIBLE);
        }
    }

    private void uiStatePaused() {
        uiResetWidgets();
        if (MODE_ADVANCED.equals(mCurrentMode)) {
            mProgressBar.setEnabled(true);
            mProgressBar.setVisibility(ProgressBar.VISIBLE);
        }
    }

    private void uiStateSlotSwitchRequired() {
        uiResetWidgets();
        if (MODE_ADVANCED.equals(mCurrentMode)) {
            mProgressBar.setEnabled(true);
            mProgressBar.setVisibility(ProgressBar.VISIBLE);
        }
    }

    private void uiStateError() {
        uiResetWidgets();
        if (MODE_ADVANCED.equals(mCurrentMode)) {
            mProgressBar.setVisibility(ProgressBar.VISIBLE);
        }
    }

    private void uiStateRebootRequired() {
        uiResetWidgets();
        if (mButtonReboot != null) {
            mButtonReboot.setVisibility(View.VISIBLE);
        }
    }

    /**
     * @param state updater sample state
     */
    private void setUiUpdaterState(int state) {
        if (mTextViewUpdaterState != null) {
            String stateText = UpdaterState.getStateText(state);
            mTextViewUpdaterState.setText(stateText + "/" + state);
        }
    }

    /**
     * Invoked when SystemUpdaterSample app state changes.
     * Value of {@code state} will be one of the
     * values from {@link UpdaterState}.
     */
    private void onUpdaterStateChange(int state) {
        Log.i(TAG, "UpdaterStateChange state="
                + UpdaterState.getStateText(state)
                + "/" + state);

        runOnUiThread(() -> {
            setUiUpdaterState(state);

            if (state == UpdaterState.IDLE) {
                uiStateIdle();
            } else if (state == UpdaterState.RUNNING) {
                uiStateRunning();
            } else if (state == UpdaterState.PAUSED) {
                uiStatePaused();
            } else if (state == UpdaterState.ERROR) {
                uiStateError();
            } else if (state == UpdaterState.SLOT_SWITCH_REQUIRED) {
                uiStateSlotSwitchRequired();
            } else if (state == UpdaterState.REBOOT_REQUIRED) {
                uiStateRebootRequired();
            }
        });
    }

    private void uiResetEngineText() {
        Fragment f = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container_view);

        if (f instanceof AdvanceFragment) {
            ((AdvanceFragment) f).setEngineStatusText(getString(R.string.unknown));
            ((AdvanceFragment) f).setEngineErrorText(getString(R.string.unknown));
        }
    }

    /**
     * Invoked when {@link UpdateEngine} status changes. Value of {@code status} will
     * be one of the values from {@link UpdateEngine.UpdateStatusConstants}.
     */
    private void onEngineStatusUpdate(int status) {
        Log.i(TAG, "StatusUpdate - status="
                + UpdateEngineStatuses.getStatusText(status)
                + "/" + status);
        runOnUiThread(() -> {
            setUiEngineStatus(status);
        });
    }

    /**
     * @param status update engine status code
     */
    private void setUiEngineStatus(int status) {
        String statusText = UpdateEngineStatuses.getStatusText(status);
        Fragment f = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container_view);

        if (f instanceof AdvanceFragment) {
            ((AdvanceFragment) f).setEngineStatusText(statusText + "/" + status);
        }
    }

    /**
     * @param errorCode update engine error code
     */
    private void setUiEngineErrorCode(int errorCode) {
        Fragment f = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container_view);

        if (f instanceof AdvanceFragment) {
            String errorText = UpdateEngineErrorCodes.getCodeName(errorCode);
            ((AdvanceFragment) f).setEngineErrorText(errorText + "/" + errorCode);
        }
    }

    /**
     * Invoked when the payload has been applied, whether successfully or
     * unsuccessfully. The value of {@code errorCode} will be one of the
     * values from {@link UpdateEngine.ErrorCodeConstants}.
     */
    private void onEnginePayloadApplicationComplete(int errorCode) {
        final String completionState = UpdateEngineErrorCodes.isUpdateSucceeded(errorCode)
                ? "SUCCESS"
                : "FAILURE";
        Log.i(TAG,
                "PayloadApplicationCompleted - errorCode="
                        + UpdateEngineErrorCodes.getCodeName(errorCode) + "/" + errorCode
                        + " " + completionState);
        runOnUiThread(() -> {
            setUiEngineErrorCode(errorCode);

            // Handle automatic fallback from incremental to full update on failure
//            if (!UpdateEngineErrorCodes.isUpdateSucceeded(errorCode) &&
//                    mIsIncrementalUpdate && !mHasTriedFullUpdate) {
//                handleIncrementalUpdateFailure();
//            }
        });
    }

    private UpdateConfig getSelectedConfig() {
        if (mConfigs == null || mConfigs.isEmpty()) {
            return null;
        }
        return mConfigs.get(mConfigs.size() - 1);
    }

    /**
     * loads json configurations from configs dir that is defined in {@link UpdateConfigs}.
     */
    private void loadUpdateConfigs() {
        mConfigs = UpdateConfigs.getUpdateConfigs(this);
    }

    private void applyUpdate(UpdateConfig config) {
        if (config == null) {
            DialogHelper.show(this, DialogHelper.Type.ERROR,"No Config Selected", "No update configuration selected. Please select a config file first.");
            return;
        }
        try {
            // Check if we're in ERROR state and reset to IDLE first
            if (mUpdateManager.getUpdaterState() == UpdaterState.ERROR) {
                Log.i(TAG, "Resetting from ERROR state to IDLE before applying update");
                mUpdateManager.resetUpdate();
                // Add a small delay to ensure state reset completes
                new Handler().postDelayed(() -> {
                    try {
                        mUpdateManager.applyUpdate(this, config);
                    } catch (UpdaterState.InvalidTransitionException ex) {
                        Log.e(TAG, "Failed to apply update after state reset " + config.getName(), ex);
                    }
                }, 100);
                return;
            }
            mUpdateManager.applyUpdate(this, config);
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to apply update " + config.getName(), e);
        }
    }

    public void onInstallClick() {
        if (mConfigs == null || mConfigs.isEmpty()) {
            DialogHelper.show(this, DialogHelper.Type.ERROR,"No Updates", "No update configurations available. Please check status first.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Update new version")
                .setMessage("Do you want to install the selected update?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    uiResetWidgets();
                    uiResetEngineText();
                    applyUpdate(getSelectedConfig());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * download config button is clicked
     */
    public void onDownloadConfigClick(String configUrl) {

        if (mButtonReboot != null) {
            mButtonReboot.setVisibility(View.GONE);
        }

        // Use foreground service for persistent download
        String downloadId = mUpdateStateManager.generateDownloadId();
        UpdateStateManager.ActiveDownload activeDownload =
                new UpdateStateManager.ActiveDownload(downloadId, configUrl);
        mUpdateStateManager.addActiveDownload(activeDownload);

        Intent intent = new Intent(this, ForegroundConfigDownloadService.class);
        intent.putExtra(ForegroundConfigDownloadService.EXTRA_CONFIG_URL, configUrl);
        intent.putExtra(ForegroundConfigDownloadService.EXTRA_DOWNLOAD_ID, downloadId);
        startService(intent);

        Log.i(TAG, "Started foreground config download: " + downloadId );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mode_menu, menu);
        // Update menu items based on current mode
        MenuItem easyModeItem = menu.findItem(R.id.menu_easy_mode);
        MenuItem advancedModeItem = menu.findItem(R.id.menu_advanced_mode);

        if (MODE_EASY.equals(mCurrentMode)) {
            easyModeItem.setVisible(false);
            advancedModeItem.setVisible(true);
        } else {
            easyModeItem.setVisible(true);
            advancedModeItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Log.i(TAG, "Options menu item clicked: " + id);

        if (id == R.id.menu_easy_mode) {
            switchToMode(MODE_EASY);
            return true;
        } else if (id == R.id.menu_advanced_mode) {
            switchToMode(MODE_ADVANCED);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void switchToMode(String newMode) {
        if (!newMode.equals(mCurrentMode)) {
            mCurrentMode = newMode;

            mSharedPreferences.edit()
                    .putString(PREF_MODE, mCurrentMode)
                    .putBoolean(PREF_MODE_SWITCH, true)
                    .apply();

            Fragment fragment;

            if (MODE_EASY.equals(newMode)) {
                fragment = new EasyFragment();
            } else {
                fragment = new AdvanceFragment();
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.fragment_container_view, fragment)
                    .commit();

            invalidateOptionsMenu(); // refresh menu visibility
        }
    }

    /**
     * Setup broadcast receiver to handle updates from foreground services
     */
    private void setupBroadcastReceiver() {
        mBroadcastReceiver = new UpdateBroadcastReceiver();
        mBroadcastReceiver.addListener(new UpdateBroadcastReceiver.UpdateListener() {
            @Override
            public void onDownloadStarted(String downloadId) {
               runOnUiThread(() -> {
                    Log.i(TAG, "Config download started: " + downloadId);
                    uiResetWidgets();
                });
            }

            @Override
            public void onDownloadSuccess(String downloadId, String filename) {
                runOnUiThread(() -> {
                    loadUpdateConfigs(); // Reload configs to show the new one

                    mUpdateStateManager.removeAllDownloads();

                    String versionStr = SystemPropertiesHelper.getVersion();
                    String nameConfig = mConfigs.get(mConfigs.size() - 1).getName();
                    String configVersionStr = nameConfig.split("_Ver")[1];

                    int result = compareVersion(configVersionStr, versionStr);

                    Log.i(TAG, "Current=" + versionStr + " Config=" + configVersionStr);
                    if (result >= 1 && nameConfig.contains("Incremental")) {
                        Log.i(TAG, "New version found, but it is not compatible with the current version.");
                        mIsIncrementalUpdate = false;
                        mHasTriedFullUpdate = true;
                        onDownloadConfigClick(CONFIG_URL_FULL);
                        return;
                    } else if (result > 0) {
                        uiStateIdle();
                        mIsNewVersion = true;
                        triggerOnValidUpdate(true);
                        Log.i(TAG, "New version found, but it is not compatible with the current version.");
                    } else{
                        Log.i(TAG, "No new version found.");
                        mIsNewVersion = false;
                        DialogHelper.show(MainActivity.this, DialogHelper.Type.SUCCESS,"", "No new version found.");
                        return;
                    }

                    if (mIsApply || mHasTriedFullUpdate) {
                        triggerOnValidUpdate(false);
                        uiResetEngineText();
                        applyUpdate(getSelectedConfig());
                        mIsApply = false;
                        // Reset the fallback flag after applying
                        if (mHasTriedFullUpdate) {
                            mHasTriedFullUpdate = false;
                        }
                    } else {
                        DialogHelper.show(MainActivity.this, DialogHelper.Type.SUCCESS,"Success", "Config file downloaded successfully");
                    }

                });
            }

            @Override
            public void onDownloadError(String downloadId, String errorMessage) {
                runOnUiThread(() -> {
                    DialogHelper.show(MainActivity.this, DialogHelper.Type.ERROR,"Download Failed", errorMessage);

                    mUpdateStateManager.removeAllDownloads();
                });
            }

            @Override
            public void onDownloadProgress(String downloadId, int progress) {
                runOnUiThread(() -> {
                    mUpdateStateManager.updateDownloadProgress(downloadId, progress);
                    // Update UI if needed
                });
            }

            @Override
            public void onPrepareStarted(String updateId) {
                runOnUiThread(() -> {
                    Log.i(TAG, "Update preparation started: " + updateId);
                });
            }

            @Override
            public void onPrepareSuccess(String updateId) {
                runOnUiThread(() -> {
                    Log.i(TAG, "Update preparation completed: " + updateId);
                    mUpdateStateManager.removeActiveUpdate(updateId);
                });
            }

            @Override
            public void onPrepareError(String updateId, String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Update preparation failed: " + updateId + ", error: " + errorMessage);
                    DialogHelper.show(MainActivity.this, DialogHelper.Type.ERROR,"Update Preparation Failed", errorMessage);
                    mUpdateStateManager.removeActiveUpdate(updateId);
                });
            }

            @Override
            public void onPrepareProgress(String updateId, int progress) {
                runOnUiThread(() -> {
                    mUpdateStateManager.updateUpdateProgress(updateId, progress);
                    // Update UI if needed
                });
            }
        });

        // Register for all relevant broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(ForegroundConfigDownloadService.ACTION_DOWNLOAD_STARTED);
        filter.addAction(ForegroundConfigDownloadService.ACTION_DOWNLOAD_SUCCESS);
        filter.addAction(ForegroundConfigDownloadService.ACTION_DOWNLOAD_ERROR);
        filter.addAction(ForegroundConfigDownloadService.ACTION_DOWNLOAD_PROGRESS);
        filter.addAction(ForegroundPrepareUpdateService.ACTION_PREPARE_STARTED);
        filter.addAction(ForegroundPrepareUpdateService.ACTION_PREPARE_SUCCESS);
        filter.addAction(ForegroundPrepareUpdateService.ACTION_PREPARE_ERROR);
        filter.addAction(ForegroundPrepareUpdateService.ACTION_PREPARE_PROGRESS);

        ContextCompat.registerReceiver(this, mBroadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public static int compareVersion(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");

        int len = Math.max(p1.length, p2.length);

        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? Integer.parseInt(p1[i]) : 0;
            int n2 = i < p2.length ? Integer.parseInt(p2[i]) : 0;

            if (n1 != n2) {
                return n1 - n2;
            }
        }
        return 0;
    }

    @Override
    public void onCheckStatus() {
        mIsIncrementalUpdate = true;
        mHasTriedFullUpdate = false;
        onDownloadConfigClick(CONFIG_URL_INCREMENTAL);
    }

    @Override
    public void onUpdate() {
        onInstallClick();
    }

    /**
     * Triggers onValidUpdate on the current fragment if it implements UpdateListener
     */
    private void triggerOnValidUpdate(boolean valid) {
        Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container_view);
        if (fragment instanceof UpdateListener) {
            ((UpdateListener) fragment).onValidUpdate(valid);
        }
    }
}