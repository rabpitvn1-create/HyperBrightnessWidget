package com.ericko.redmiscreenbrightness;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.provider.Settings;

public class BackgroundBrightnessService extends Service implements SensorEventListener {
    private static final String PREFS = "hyper_brightness";
    private static final String KEY_SENSOR_BAND = "sensor_band";

    private static final int BAND_NORMAL = 0;
    private static final int BAND_TOO_DARK = 1;
    private static final int BAND_TOO_BRIGHT = 2;

    private static final float NORMAL_TO_DARK_LUX = 35.0f;
    private static final float DARK_TO_NORMAL_LUX = 70.0f;
    private static final float NORMAL_TO_BRIGHT_LUX = 800.0f;
    private static final float BRIGHT_TO_NORMAL_LUX = 550.0f;

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean registered;
    private int band = BAND_NORMAL;

    public static void start(Context context) {
        try {
            context.startService(new Intent(context, BackgroundBrightnessService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        band = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SENSOR_BAND, BAND_NORMAL);
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

        if (band == BAND_NORMAL) {
            if (lux < NORMAL_TO_DARK_LUX) {
                applyBand(BAND_TOO_DARK, 20, 11);
            } else if (lux >= NORMAL_TO_BRIGHT_LUX) {
                applyBand(BAND_TOO_BRIGHT, 60, 49);
            }
            return;
        }

        if (band == BAND_TOO_DARK) {
            if (lux > DARK_TO_NORMAL_LUX) {
                applyBand(BAND_NORMAL, 30, 17);
            }
            return;
        }

        if (band == BAND_TOO_BRIGHT && lux < BRIGHT_TO_NORMAL_LUX) {
            applyBand(BAND_NORMAL, 30, 17);
        }
    }

    private void applyBand(int newBand, int percent, int raw) {
        boolean applied = HyperBrightnessTileService.applyBrightness(this, percent, raw);
        if (!applied) {
            return;
        }
        band = newBand;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_SENSOR_BAND, newBand)
                .apply();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
