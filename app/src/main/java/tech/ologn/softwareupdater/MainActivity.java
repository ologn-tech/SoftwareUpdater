package tech.ologn.softwareupdater;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

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
    private Spinner mSpinnerConfigs;
    private TextView mTextViewConfigsDirHint;
    private Button mButtonDownloadConfig;
    private Button mButtonViewConfig;
    private Button mButtonReload;
    private Button mButtonApplyConfig;
    private Button mButtonReboot;
    private Button mButtonCheckStatus;
    private Button mButtonInstall;
    private ProgressBar mProgressBar;
    private TextView mTextViewUpdaterState;
    private TextView mTextViewEngineStatus;
    private TextView mTextViewEngineErrorCode;

//    private List<UpdateConfig> mConfigs;
//    private UpdateStateManager mUpdateStateManager;
//    private UpdateBroadcastReceiver mBroadcastReceiver;
    private SharedPreferences mSharedPreferences;
    private String mCurrentMode = MODE_EASY;

//    private final UpdateManager mUpdateManager =
//            new UpdateManager(new UpdateEngine(), new Handler());

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
        if (MODE_EASY.equals(mCurrentMode) && mCurrentSwitch ) {
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

        uiResetWidgets();
    }

    private void setupLayoutPreferences() {
        mTextViewBuild = findViewById(R.id.textViewBuild);

    }

    /** resets ui */
    private void uiResetWidgets() {
        mTextViewBuild.setText(Build.DISPLAY);
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
}