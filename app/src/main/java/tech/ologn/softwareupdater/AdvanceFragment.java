package tech.ologn.softwareupdater;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

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
    List<UpdateConfig> mConfigs;
    private Spinner mSpinnerConfigs;

    public AdvanceFragment() {
        // Required empty public constructor
    }
    public static AdvanceFragment newInstance(String param1, String param2) {
        return new AdvanceFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        TextView mTextViewConfigsDirHint = view.findViewById(R.id.textViewConfigsDirHint);
        mTextViewConfigsDirHint.setText(UpdateConfigs.getConfigsRoot(requireContext()));

        mSpinnerConfigs = view.findViewById(R.id.spinnerConfigs);

        loadUpdateConfigs();

        Button mButtonViewConfig = view.findViewById(R.id.buttonViewConfig);
        mButtonViewConfig.setOnClickListener(v -> onViewConfigClick());
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
}