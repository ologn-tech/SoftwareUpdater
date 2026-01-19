package tech.ologn.softwareupdater;

import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class EasyFragment extends Fragment {

    private static final String TAG = "EasyFragment";
    ModeActionListener listener;

    Button mButtonCheckStatus;
    private UpdateBroadcastReceiver.UpdateListener mBroadcastListener;

    public EasyFragment() {
        // Required empty public constructor
    }

    public static EasyFragment newInstance(String param1, String param2) {
        return new EasyFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listener = (ModeActionListener) getContext();
        
        // Create listener (will be registered in onResume)
        mBroadcastListener = new UpdateBroadcastReceiver.UpdateListener() {
            @Override
            public void onDownloadStarted(String downloadId) {
                mButtonCheckStatus.setText("Checking...");
                mButtonCheckStatus.setEnabled(false);
            }

                @Override
                public void onDownloadSuccess(String downloadId, String filename) {
                    mButtonCheckStatus.setText("Check for new version");
                    mButtonCheckStatus.setEnabled(true);
                }

                @Override
                public void onDownloadError(String downloadId, String errorMessage) {
                    mButtonCheckStatus.setText("Check for new version");
                    mButtonCheckStatus.setEnabled(true);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_easy, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mButtonCheckStatus = view.findViewById(R.id.buttonCheckStatus);
        mButtonCheckStatus.setOnClickListener(v -> listener.onCheckStatus());
    }
}