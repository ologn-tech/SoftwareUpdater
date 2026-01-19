package tech.ologn.softwareupdater.utils;

import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Helper class to get system properties using various methods
 */
public class SystemPropertiesHelper {

    private static final String TAG = "SystemPropertiesHelper";

    /**
     * Get a system property using SystemProperties (requires system app)
     *
     * @param key the property key
     * @param defaultValue default value if property not found
     * @return property value or default value
     */
    public static String getProperty(String key, String defaultValue) {
        try {
            String value = SystemProperties.get(key, defaultValue);
            Log.d(TAG, "SystemProperties.get(" + key + ") = " + value);
            return value;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get property " + key + " via SystemProperties", e);
            return getPropertyViaCommand(key, defaultValue);
        }
    }

    /**
     * Get a system property using getprop command
     *
     * @param key the property key
     * @param defaultValue default value if property not found
     * @return property value or default value
     */
    public static String getPropertyViaCommand(String key, String defaultValue) {
        try {
            Process process = Runtime.getRuntime().exec("getprop " + key);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();

            if (line != null && !line.trim().isEmpty()) {
                Log.d(TAG, "getprop " + key + " = " + line.trim());
                return line.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get property " + key + " via getprop", e);
        }

        Log.d(TAG, "Property " + key + " not found, using default: " + defaultValue);
        return defaultValue;
    }

    /**
     * Get product model
     */
    public static String getProductModel() {
        return getProperty("ro.product.odm.model", "Unknown");
    }
    /**
     * Get build date
     *
     */
    public static String getBuildDate() {
        return getProperty("ro.vendor.build.date.utc", "Unknown");
    }

    /**
     * Get version
     */
    public static String getVersion() {
        // Try getprop command first since SystemProperties might not have access to vendor properties
        String value = getProperty("ro.vendor.software.version", "Unknown");
        if ("Unknown".equals(value)) {
            // Fallback to SystemProperties if getprop fails
            value = getPropertyViaCommand("ro.vendor.software.version", "Unknown");
        }
        return value;
    }

}
