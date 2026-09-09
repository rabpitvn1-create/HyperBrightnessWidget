package com.ericko.redmiscreenbrightness;

import android.content.Context;
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

    @Override
    public void onStartListening() {
        super.onStartListening();
        reapplyIfEnabled(this);
        updateTileLabel(this);
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!Settings.System.canWrite(this)) {
            updateTileLabel(this);
            return;
        }

        boolean enabled = isEnabled(this);
        setEnabled(this, !enabled);
        updateTileLabel(this);
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        if (!Settings.System.canWrite(context)) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        try {
            if (enabled) {
                if (!prefs.getBoolean(KEY_ENABLED, false)) {
                    float previousAdjustment = Settings.System.getFloat(
                            context.getContentResolver(),
                            AUTO_BRIGHTNESS_ADJ_KEY,
                            0.0f
                    );
                    prefs.edit()
                            .putFloat(KEY_PREVIOUS_ADJ, previousAdjustment)
                            .putBoolean(KEY_PREVIOUS_ADJ_SAVED, true)
                            .apply();
                }

                Settings.System.putInt(
                        context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                );
                Settings.System.putFloat(
                        context.getContentResolver(),
                        AUTO_BRIGHTNESS_ADJ_KEY,
                        AUTO_BRIGHTNESS_ADJ
                );
                prefs.edit().putBoolean(KEY_ENABLED, true).apply();
                return true;
            }

            float restoreAdjustment = prefs.getBoolean(KEY_PREVIOUS_ADJ_SAVED, false)
                    ? prefs.getFloat(KEY_PREVIOUS_ADJ, 0.0f)
                    : 0.0f;

            Settings.System.putFloat(
                    context.getContentResolver(),
                    AUTO_BRIGHTNESS_ADJ_KEY,
                    restoreAdjustment
            );
            prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putBoolean(KEY_PREVIOUS_ADJ_SAVED, false)
                    .apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean reapplyIfEnabled(Context context) {
        if (!isEnabled(context)) {
            return false;
        }
        if (!Settings.System.canWrite(context)) {
            return false;
        }

        try {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            );
            Settings.System.putFloat(
                    context.getContentResolver(),
                    AUTO_BRIGHTNESS_ADJ_KEY,
                    AUTO_BRIGHTNESS_ADJ
            );
            return true;
        } catch (Exception ignored) {
            return false;
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
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateTileLabel(Context context) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean canWrite = Settings.System.canWrite(context);
        boolean enabled = isEnabled(context);

        if (!canWrite) {
            tile.setLabel("Grant permission");
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
