package com.ericko.redmiscreenbrightness;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.provider.Settings;

public class BackgroundBrightnessService extends Service implements SensorEventListener {
    private static final float NORMAL_TO_DARK_LUX = 15.0f;
    private static final float DARK_TO_NORMAL_LUX = 70.0f;
    private static final float NORMAL_TO_BRIGHT_LUX = 800.0f;
    private static final float BRIGHT_TO_NORMAL_LUX = 550.0f;

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean registered;
    private int band = HyperBrightnessTileService.BAND_NORMAL;

    public static void start(Context context) {
        try {
            context.startService(new Intent(context, BackgroundBrightnessService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        band = getSharedPreferences(HyperBrightnessTileService.PREFS, MODE_PRIVATE)
                .getInt(HyperBrightnessTileService.KEY_SENSOR_BAND, HyperBrightnessTileService.BAND_NORMAL);
        registerLightSensor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerLightSensor();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (sensorManager != null && registered) {
            sensorManager.unregisterListener(this);
            registered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerLightSensor() {
        if (!Settings.System.canWrite(this)) {
            return;
        }
        if (registered) {
            return;
        }
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            return;
        }
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor == null) {
            return;
        }
        registered = sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length == 0) {
            return;
        }

        float lux = event.values[0];
        SharedPreferences prefs = getSharedPreferences(HyperBrightnessTileService.PREFS, MODE_PRIVATE);
        prefs.edit().putFloat(HyperBrightnessTileService.KEY_LAST_LUX, lux).apply();

        if (manualOverrideBlocksAuto(prefs, lux)) {
            return;
        }

        if (band == HyperBrightnessTileService.BAND_NORMAL) {
            if (lux < NORMAL_TO_DARK_LUX) {
                applyBand(HyperBrightnessTileService.BAND_TOO_DARK, 20, 11);
            } else if (lux >= NORMAL_TO_BRIGHT_LUX) {
                applyBand(HyperBrightnessTileService.BAND_TOO_BRIGHT, 60, 49);
            }
            return;
        }

        if (band == HyperBrightnessTileService.BAND_TOO_DARK) {
            if (lux > DARK_TO_NORMAL_LUX) {
                applyBand(HyperBrightnessTileService.BAND_NORMAL, 30, 17);
            }
            return;
        }

        if (band == HyperBrightnessTileService.BAND_TOO_BRIGHT && lux < BRIGHT_TO_NORMAL_LUX) {
            applyBand(HyperBrightnessTileService.BAND_NORMAL, 30, 17);
        }
    }

    private boolean manualOverrideBlocksAuto(SharedPreferences prefs, float lux) {
        if (!prefs.getBoolean(HyperBrightnessTileService.KEY_MANUAL_OVERRIDE, false)) {
            return false;
        }

        int overrideBand = prefs.getInt(
                HyperBrightnessTileService.KEY_MANUAL_OVERRIDE_BAND,
                band
        );

        if (overrideBand == HyperBrightnessTileService.BAND_TOO_DARK) {
            if (lux <= DARK_TO_NORMAL_LUX) {
                return true;
            }
            clearManualOverride(prefs, HyperBrightnessTileService.BAND_NORMAL);
            return false;
        }

        if (overrideBand == HyperBrightnessTileService.BAND_TOO_BRIGHT) {
            if (lux >= BRIGHT_TO_NORMAL_LUX) {
                return true;
            }
            clearManualOverride(prefs, HyperBrightnessTileService.BAND_NORMAL);
            return false;
        }

        if (lux >= NORMAL_TO_DARK_LUX && lux < NORMAL_TO_BRIGHT_LUX) {
            return true;
        }

        clearManualOverride(prefs, HyperBrightnessTileService.BAND_NORMAL);
        return false;
    }

    private void clearManualOverride(SharedPreferences prefs, int newBand) {
        band = newBand;
        prefs.edit()
                .putBoolean(HyperBrightnessTileService.KEY_MANUAL_OVERRIDE, false)
                .putInt(HyperBrightnessTileService.KEY_SENSOR_BAND, newBand)
                .apply();
    }

    private void applyBand(int newBand, int percent, int raw) {
        boolean applied = HyperBrightnessTileService.applyAutomaticBrightness(this, percent, raw);
        if (!applied) {
            return;
        }
        band = newBand;
        getSharedPreferences(HyperBrightnessTileService.PREFS, MODE_PRIVATE)
                .edit()
                .putInt(HyperBrightnessTileService.KEY_SENSOR_BAND, newBand)
                .apply();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
