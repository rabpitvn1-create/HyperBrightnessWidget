package com.ericko.redmiscreenbrightness;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HyperBrightnessTileService extends TileService {
    public static final String PREFS = "hyper_brightness";
    public static final String KEY_PERCENT = "brightness_percent";
    public static final String KEY_RAW = "brightness_raw";
    public static final String KEY_SENSOR_BAND = "sensor_band";
    public static final String KEY_MANUAL_OVERRIDE = "manual_override";
    public static final String KEY_MANUAL_OVERRIDE_BAND = "manual_override_band";
    public static final String KEY_LAST_LUX = "last_lux";

    public static final int BAND_NORMAL = 0;
    public static final int BAND_TOO_DARK = 1;
    public static final int BAND_TOO_BRIGHT = 2;

    private static final float DARK_HOLD_UNTIL_LUX = 70.0f;
    private static final float BRIGHT_HOLD_UNTIL_LUX = 550.0f;
    private static final float BRIGHT_START_LUX = 800.0f;

    private static final int[][] LEVELS = new int[][]{
            {20, 11},
            {30, 17},
            {40, 26},
            {50, 38},
            {60, 49}
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileLabel(this);
    }

    @Override
    public void onClick() {
        super.onClick();
        int currentPercent = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PERCENT, 30);
        int nextIndex = 0;
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i][0] == currentPercent) {
                nextIndex = (i + 1) % LEVELS.length;
                break;
            }
        }
        applyBrightness(this, LEVELS[nextIndex][0], LEVELS[nextIndex][1]);
        BackgroundBrightnessService.start(this);
        updateTileLabel(this);
    }

    public static boolean applyBrightness(Context context, int percent, int raw) {
        return applyBrightnessInternal(context, percent, raw, true);
    }

    public static boolean applyAutomaticBrightness(Context context, int percent, int raw) {
        return applyBrightnessInternal(context, percent, raw, false);
    }

    private static boolean applyBrightnessInternal(Context context, int percent, int raw, boolean manualWrite) {
        if (!Settings.System.canWrite(context)) {
            return false;
        }

        try {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            );
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    raw
            );

            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit()
                    .putInt(KEY_PERCENT, percent)
                    .putInt(KEY_RAW, raw);

            if (manualWrite) {
                editor.putBoolean(KEY_MANUAL_OVERRIDE, true)
                        .putInt(KEY_MANUAL_OVERRIDE_BAND, resolveManualOverrideBand(prefs));
            }

            editor.apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int resolveManualOverrideBand(SharedPreferences prefs) {
        int currentBand = prefs.getInt(KEY_SENSOR_BAND, BAND_NORMAL);
        float lastLux = prefs.getFloat(KEY_LAST_LUX, Float.NaN);

        if (Float.isNaN(lastLux)) {
            return currentBand;
        }

        if (currentBand == BAND_TOO_DARK || lastLux < DARK_HOLD_UNTIL_LUX) {
            return BAND_TOO_DARK;
        }

        if (currentBand == BAND_TOO_BRIGHT || lastLux >= BRIGHT_START_LUX || lastLux >= BRIGHT_HOLD_UNTIL_LUX) {
            return BAND_TOO_BRIGHT;
        }

        return BAND_NORMAL;
    }

    private void updateTileLabel(Context context) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        int percent = context.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PERCENT, 30);
        tile.setLabel(percent + "%");
        tile.setState(Settings.System.canWrite(context) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
