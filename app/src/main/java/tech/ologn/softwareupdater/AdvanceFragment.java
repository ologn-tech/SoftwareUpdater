package tech.ologn.softwareupdater;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class AdvanceFragment extends Fragment {

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_advance, container, false);
    }
}