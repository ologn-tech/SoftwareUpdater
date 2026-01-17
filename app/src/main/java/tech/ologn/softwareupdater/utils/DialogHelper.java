package tech.ologn.softwareupdater.utils;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

public final class DialogHelper {

    private DialogHelper() {
        // prevent instantiation
    }

    public enum Type {
        ERROR,
        SUCCESS
    }

    public static void show(Context context, Type type, String title, String message) {
        int iconRes;

        if (type == Type.ERROR) {
            iconRes = android.R.drawable.ic_dialog_alert;
        } else {
            iconRes = android.R.drawable.ic_dialog_info;
        }

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setIcon(iconRes)
                .setPositiveButton(android.R.string.ok, (dialog, id) -> dialog.dismiss())
                .show();
    }
}
