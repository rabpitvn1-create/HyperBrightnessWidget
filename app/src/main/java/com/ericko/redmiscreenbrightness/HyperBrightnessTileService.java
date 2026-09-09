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
    private static final String KEY_PREVIOUS_ADJ = "previous_auto_brightness_adj";
    private static final String KEY_PREVIOUS_ADJ_SAVED = "previous_auto_brightness_adj_saved";

    private static final String AUTO_BRIGHTNESS_ADJ_KEY = "screen_auto_brightness_adj";
    private static final float AUTO_BRIGHTNESS_ADJ = -0.20f;
    private static final float VERIFY_TOLERANCE = 0.02f;

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
                if (!prefs.getBoolean(KEY_ENABLED, false)) {
                    float previousAdjustment = getCurrentAdjustment(context);
                    prefs.edit()
                            .putFloat(KEY_PREVIOUS_ADJ, previousAdjustment)
                            .putBoolean(KEY_PREVIOUS_ADJ_SAVED, true)
                            .apply();
                }

                if (!applyMinus20WithShizuku()) {
                    return false;
                }

                if (!verifyEnabledState(context)) {
                    return false;
                }

                prefs.edit().putBoolean(KEY_ENABLED, true).apply();
                return true;
            }

            float restoreAdjustment = prefs.getBoolean(KEY_PREVIOUS_ADJ_SAVED, false)
                    ? prefs.getFloat(KEY_PREVIOUS_ADJ, 0.0f)
                    : 0.0f;

            String command = "settings --user current put system "
                    + AUTO_BRIGHTNESS_ADJ_KEY + " " + Float.toString(restoreAdjustment);
            if (!ShizukuBridge.runShellCommand(command)) {
                return false;
            }

            float restored = getCurrentAdjustment(context);
            if (Math.abs(restored - restoreAdjustment) > VERIFY_TOLERANCE) {
                return false;
            }

            prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putBoolean(KEY_PREVIOUS_ADJ_SAVED, false)
                    .apply();
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
        return applyMinus20WithShizuku() && verifyEnabledState(context);
    }

    private static boolean applyMinus20WithShizuku() {
        String command = "settings --user current put system screen_brightness_mode 1"
                + " && settings --user current put system "
                + AUTO_BRIGHTNESS_ADJ_KEY + " " + Float.toString(AUTO_BRIGHTNESS_ADJ);
        return ShizukuBridge.runShellCommand(command);
    }

    private static boolean verifyEnabledState(Context context) {
        return isAutomaticBrightnessEnabled(context)
                && Math.abs(getCurrentAdjustment(context) - AUTO_BRIGHTNESS_ADJ) <= VERIFY_TOLERANCE;
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
