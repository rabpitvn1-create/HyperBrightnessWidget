package com.ericko.redmiscreenbrightness;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HyperBrightnessTileService extends TileService {
    public static final String PREFS = "hyper_brightness";
    public static final String KEY_ENABLED = "auto_minus_20_enabled";

    // Legacy v2.1 keys. Kept so an upgrade can restore the old adjustment once.
    private static final String KEY_PREVIOUS_ADJ = "previous_auto_brightness_adj";
    private static final String KEY_PREVIOUS_ADJ_SAVED = "previous_auto_brightness_adj_saved";
    private static final String AUTO_BRIGHTNESS_ADJ_KEY = "screen_auto_brightness_adj";

    // Android's native Reduce Bright Colors / Extra Dim settings.
    private static final String RBC_ACTIVE_KEY = "reduce_bright_colors_activated";
    private static final String RBC_LEVEL_KEY = "reduce_bright_colors_level";
    private static final int RBC_LEVEL = 20;

    private static final String KEY_PREVIOUS_RBC_ACTIVE = "previous_rbc_active";
    private static final String KEY_PREVIOUS_RBC_LEVEL = "previous_rbc_level";
    private static final String KEY_PREVIOUS_RBC_SAVED = "previous_rbc_saved";

    @Override
    public void onStartListening() {
        super.onStartListening();
        reapplyIfEnabled(this);
        updateTileLabel(this);
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!ShizukuBridge.isAvailable() || !ShizukuBridge.hasPermission()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivityAndCollapse(intent);
            } catch (Throwable ignored) {
                try {
                    startActivity(intent);
                } catch (Throwable ignoredAgain) {
                }
            }
            updateTileLabel(this);
            return;
        }

        setEnabled(this, !isEnabled(this));
        updateTileLabel(this);
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        try {
            if (enabled) {
                // v2.1 wrote screen_auto_brightness_adj. HyperOS can persist that key while
                // ignoring it in the actual brightness pipeline, so restore the pre-v2.1 value.
                restoreLegacyAdjustmentIfNeeded(prefs);

                if (!prefs.getBoolean(KEY_PREVIOUS_RBC_SAVED, false)) {
                    prefs.edit()
                            .putInt(KEY_PREVIOUS_RBC_ACTIVE, getReduceBrightColorsActivated(context) ? 1 : 0)
                            .putInt(KEY_PREVIOUS_RBC_LEVEL, getReduceBrightColorsLevel(context))
                            .putBoolean(KEY_PREVIOUS_RBC_SAVED, true)
                            .apply();
                }

                if (!applyNativeDim()) {
                    return false;
                }

                if (!verifyEnabledState(context)) {
                    return false;
                }

                prefs.edit().putBoolean(KEY_ENABLED, true).apply();
                return true;
            }

            int restoreActive = prefs.getBoolean(KEY_PREVIOUS_RBC_SAVED, false)
                    ? prefs.getInt(KEY_PREVIOUS_RBC_ACTIVE, 0)
                    : 0;
            int restoreLevel = prefs.getBoolean(KEY_PREVIOUS_RBC_SAVED, false)
                    ? prefs.getInt(KEY_PREVIOUS_RBC_LEVEL, 50)
                    : 50;

            String command = "settings --user current put secure " + RBC_LEVEL_KEY + " " + restoreLevel
                    + " && settings --user current put secure " + RBC_ACTIVE_KEY + " " + restoreActive;
            if (!ShizukuBridge.runShellCommand(command)) {
                return false;
            }

            if (getReduceBrightColorsActivated(context) != (restoreActive == 1)) {
                return false;
            }

            prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putBoolean(KEY_PREVIOUS_RBC_SAVED, false)
                    .apply();
            restoreLegacyAdjustmentIfNeeded(prefs);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean reapplyIfEnabled(Context context) {
        if (!isEnabled(context)) {
            return false;
        }
        if (!ShizukuBridge.isAvailable() || !ShizukuBridge.hasPermission()) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        restoreLegacyAdjustmentIfNeeded(prefs);
        return applyNativeDim() && verifyEnabledState(context);
    }

    private static boolean applyNativeDim() {
        String command = "settings --user current put system screen_brightness_mode 1"
                + " && settings --user current put secure " + RBC_LEVEL_KEY + " " + RBC_LEVEL
                + " && settings --user current put secure " + RBC_ACTIVE_KEY + " 1";
        return ShizukuBridge.runShellCommand(command);
    }

    private static boolean verifyEnabledState(Context context) {
        return isAutomaticBrightnessEnabled(context)
                && getReduceBrightColorsActivated(context)
                && getReduceBrightColorsLevel(context) == RBC_LEVEL;
    }

    private static void restoreLegacyAdjustmentIfNeeded(SharedPreferences prefs) {
        if (!prefs.getBoolean(KEY_PREVIOUS_ADJ_SAVED, false)) {
            return;
        }

        float previousAdjustment = prefs.getFloat(KEY_PREVIOUS_ADJ, 0.0f);
        String command = "settings --user current put system "
                + AUTO_BRIGHTNESS_ADJ_KEY + " " + Float.toString(previousAdjustment);
        if (ShizukuBridge.runShellCommand(command)) {
            prefs.edit().putBoolean(KEY_PREVIOUS_ADJ_SAVED, false).apply();
        }
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static float getCurrentAdjustment(Context context) {
        return Settings.System.getFloat(
                context.getContentResolver(),
                AUTO_BRIGHTNESS_ADJ_KEY,
                0.0f
        );
    }

    public static boolean isAutomaticBrightnessEnabled(Context context) {
        try {
            return Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean getReduceBrightColorsActivated(Context context) {
        try {
            return Settings.Secure.getInt(
                    context.getContentResolver(),
                    RBC_ACTIVE_KEY,
                    0
            ) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int getReduceBrightColorsLevel(Context context) {
        try {
            return Settings.Secure.getInt(
                    context.getContentResolver(),
                    RBC_LEVEL_KEY,
                    50
            );
        } catch (Throwable ignored) {
            return 50;
        }
    }

    private void updateTileLabel(Context context) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean shizukuReady = ShizukuBridge.isAvailable() && ShizukuBridge.hasPermission();
        boolean enabled = isEnabled(context);

        if (!shizukuReady) {
            tile.setLabel("Setup Shizuku");
            tile.setState(Tile.STATE_INACTIVE);
        } else if (enabled) {
            tile.setLabel("Auto -20%");
            tile.setState(Tile.STATE_ACTIVE);
        } else {
            tile.setLabel("Auto normal");
            tile.setState(Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }
}
