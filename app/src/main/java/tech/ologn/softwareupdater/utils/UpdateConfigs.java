package tech.ologn.softwareupdater.utils;

import android.content.Context;
import android.util.Log;

import tech.ologn.softwareupdater.UpdateConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for working with json update configurations.
 */
public final class UpdateConfigs {

    public static final String UPDATE_CONFIGS_ROOT = "configs/";

    /**
     * @param configs update configs
     * @return list of names
     */
    public static String[] configsToNames(List<UpdateConfig> configs) {
        return configs.stream().map(UpdateConfig::getName).toArray(String[]::new);
    }

    /**
     * @param context app context
     * @return configs root directory
     */
    public static String getConfigsRoot(Context context) {
        return Paths
                .get(context.getFilesDir().toString(), UPDATE_CONFIGS_ROOT)
                .toString();
    }

    /**
     * @param context application context
     * @return list of configs from directory {@link UpdateConfigs#getConfigsRoot}
     */
    public static List<UpdateConfig> getUpdateConfigs(Context context) {
        File root = new File(getConfigsRoot(context));
        ArrayList<UpdateConfig> configs = new ArrayList<>();
        if (!root.exists()) {
            return configs;
        }
        for (final File f : root.listFiles()) {
            if (!f.isDirectory() && f.getName().endsWith(".json")) {
                try {
                    String json = new String(Files.readAllBytes(f.toPath()),
                            StandardCharsets.UTF_8);
                    configs.add(UpdateConfig.fromJson(json));
                } catch (Exception e) {
                    Log.e("UpdateConfigs", "Can't read/parse config file " + f.getName(), e);
                    throw new RuntimeException(
                            "Can't read/parse config file " + f.getName(), e);
                }
            }
        }
        return configs;
    }

    /**
     * @param filename searches by given filename
     * @param config searches in {@link UpdateConfig#getAbConfig()}
     * @return offset and size of {@code filename} in the package zip file
     *         stored as {@link UpdateConfig.PackageFile}.
     */
    public static Optional<UpdateConfig.PackageFile> getPropertyFile(
            final String filename,
            UpdateConfig config) {
        return Arrays
                .stream(config.getAbConfig().getPropertyFiles())
                .filter(file -> filename.equals(file.getFilename()))
                .findFirst();
    }

    private UpdateConfigs() {}
}
