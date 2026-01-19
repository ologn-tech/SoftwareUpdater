package tech.ologn.softwareupdater;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

import tech.ologn.softwareupdater.utils.DialogHelper;
import tech.ologn.softwareupdater.utils.UpdateConfigs;

public class AdvanceFragment extends Fragment {
    private static final String TAG = "AdvanceFragment";
    List<UpdateConfig> mConfigs;
    private Spinner mSpinnerConfigs;
    Button mButtonDownloadConfig;

    ModeActionListener listener;
    private UpdateBroadcastReceiver.UpdateListener mBroadcastListener;

    public AdvanceFragment() {
        // Required empty public constructor
    }
    public static AdvanceFragment newInstance(String param1, String param2) {
        return new AdvanceFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listener = (ModeActionListener) getContext();

        mBroadcastListener = new UpdateBroadcastReceiver.UpdateListener() {
            @Override
            public void onDownloadStarted(String downloadId) {
                mButtonDownloadConfig.setText("Downloading...");
                mButtonDownloadConfig.setEnabled(false);
            }

            @Override
            public void onDownloadSuccess(String downloadId, String filename) {
                // Reload configs when download succeeds
                if (getView() != null) {
                    loadUpdateConfigs();
                    mButtonDownloadConfig.setText("Download Config");
                    mButtonDownloadConfig.setEnabled(true);
                }
            }

            @Override
            public void onDownloadError(String downloadId, String errorMessage) {
                mButtonDownloadConfig.setText("Download Config");
                mButtonDownloadConfig.setEnabled(true);
            }

            @Override
            public void onDownloadProgress(String downloadId, int progress) {
                Log.d(TAG, "Download progress: " + downloadId + ", progress: " + progress + "%");
            }

            @Override
            public void onPrepareStarted(String updateId) {
                Log.i(TAG, "Prepare started: " + updateId);
            }

            @Override
            public void onPrepareSuccess(String updateId) {
                Log.i(TAG, "Prepare success: " + updateId);
            }

            @Override
            public void onPrepareError(String updateId, String errorMessage) {
                Log.e(TAG, "Prepare error: " + updateId + ", error: " + errorMessage);
            }

            @Override
            public void onPrepareProgress(String updateId, int progress) {
                Log.d(TAG, "Prepare progress: " + updateId + ", progress: " + progress + "%");
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register listener with MainActivity's broadcast receiver
        if (getActivity() instanceof MainActivity && mBroadcastListener != null) {
            MainActivity activity = (MainActivity) getActivity();
            if (activity.mBroadcastReceiver != null) {
                activity.mBroadcastReceiver.addListener(mBroadcastListener);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister listener when fragment is paused
        if (getActivity() instanceof MainActivity && mBroadcastListener != null) {
            MainActivity activity = (MainActivity) getActivity();
            if (activity.mBroadcastReceiver != null) {
                activity.mBroadcastReceiver.removeListener(mBroadcastListener);
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        TextView mTextViewConfigsDirHint = view.findViewById(R.id.textViewConfigsDirHint);
        mTextViewConfigsDirHint.setText(UpdateConfigs.getConfigsRoot(requireContext()));

        mSpinnerConfigs = view.findViewById(R.id.spinnerConfigs);

        loadUpdateConfigs();

        Button mButtonViewConfig = view.findViewById(R.id.buttonViewConfig);
        mButtonViewConfig.setOnClickListener(v -> onViewConfigClick());

        Button mButtonReload = view.findViewById(R.id.buttonReload);
        mButtonReload.setOnClickListener(v -> onReloadClick());

        mButtonDownloadConfig = view.findViewById(R.id.buttonDownloadConfig);
        mButtonDownloadConfig.setOnClickListener(v -> listener.onCheckStatus());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_advance, container, false);
    }

    /**
     * loads json configurations from configs dir that is defined in {@link UpdateConfigs}.
     */
    private void loadUpdateConfigs() {
        mConfigs = UpdateConfigs.getUpdateConfigs(requireContext());
        loadConfigsToSpinner(mConfigs);
    }

    private void loadConfigsToSpinner(List<UpdateConfig> configs) {
        String[] spinnerArray = UpdateConfigs.configsToNames(configs);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                spinnerArray);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout
                .simple_spinner_dropdown_item);
        mSpinnerConfigs.setAdapter(spinnerArrayAdapter);

    }

    /**
     * view config button is clicked
     */
    public void onViewConfigClick() {
        if (mConfigs == null || mConfigs.isEmpty()) {
            DialogHelper.show(requireContext(),
                    DialogHelper.Type.ERROR,
                    "No Config Files",
                    "No update configurations available. Please download a config file first.");
            return;
        }
        UpdateConfig config = mConfigs.get(mSpinnerConfigs.getSelectedItemPosition());
        new AlertDialog.Builder(requireContext())
                .setTitle(config.getName())
                .setMessage(config.getRawJson())
                .setNegativeButton("Close", (dialog, id) -> dialog.dismiss())
                .show();
    }

    /**
     * reload button is clicked
     */
    public void onReloadClick() {
        loadUpdateConfigs();
    }
}