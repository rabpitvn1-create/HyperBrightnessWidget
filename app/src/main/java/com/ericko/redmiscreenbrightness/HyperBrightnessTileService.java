package com.ericko.redmiscreenbrightness;

import android.content.Context;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HyperBrightnessTileService extends TileService {
    private static final String PREFS = "hyper_brightness";
    private static final String KEY_PERCENT = "brightness_percent";
    private static final String KEY_RAW = "brightness_raw";

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
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_PERCENT, percent)
                    .putInt(KEY_RAW, raw)
                    .apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
