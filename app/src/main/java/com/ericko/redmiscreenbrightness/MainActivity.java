package com.ericko.redmiscreenbrightness;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (HyperBrightnessTileService.isEnabled(this)) {
            HyperBrightnessTileService.reapplyIfEnabled(this);
        }
        refreshStatus();
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Auto Brightness -20%");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusText, fullWidth());

        Button openShizukuButton = new Button(this);
        openShizukuButton.setText("Open Shizuku");
        openShizukuButton.setOnClickListener(v -> openShizukuManager());
        root.addView(openShizukuButton, fullWidth());

        Button shizukuPermissionButton = new Button(this);
        shizukuPermissionButton.setText("Grant Shizuku permission");
        shizukuPermissionButton.setOnClickListener(v -> requestShizukuPermission());
        root.addView(shizukuPermissionButton, fullWidth());

        Button enableButton = new Button(this);
        enableButton.setText("Enable Auto -20%");
        enableButton.setOnClickListener(v -> {
            if (!ensureShizukuReady()) {
                refreshStatus();
                return;
            }

            boolean ok = HyperBrightnessTileService.setEnabled(this, true);
            Toast.makeText(
                    this,
                    ok ? "Auto brightness ON, adjustment set to -0.20" : operationError("Enable failed"),
                    Toast.LENGTH_LONG
            ).show();
            refreshStatus();
        });
        root.addView(enableButton, fullWidth());

        Button disableButton = new Button(this);
        disableButton.setText("Disable -20% bias");
        disableButton.setOnClickListener(v -> {
            if (!ensureShizukuReady()) {
                refreshStatus();
                return;
            }

            boolean ok = HyperBrightnessTileService.setEnabled(this, false);
            Toast.makeText(
                    this,
                    ok ? "Previous auto-brightness adjustment restored" : operationError("Disable failed"),
                    Toast.LENGTH_LONG
            ).show();
            refreshStatus();
        });
        root.addView(disableButton, fullWidth());

        TextView note = new TextView(this);
        note.setText(
                "How it works:\n" +
                "• HyperOS keeps Automatic brightness enabled.\n" +
                "• The app does not read the ambient-light sensor or choose brightness levels itself.\n" +
                "• Android marks screen_auto_brightness_adj as a private system setting, so the write is performed through Shizuku shell access.\n" +
                "• Enable writes screen_brightness_mode = 1 and screen_auto_brightness_adj = -0.20.\n" +
                "• Disable restores the adjustment value saved before Enable.\n\n" +
                "Shizuku must be installed, started, and authorized for this app. The old Modify system settings permission is no longer required."
        );
        note.setTextSize(14);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, fullWidth());

        return scrollView;
    }

    private boolean ensureShizukuReady() {
        if (!ShizukuBridge.isAvailable()) {
            Toast.makeText(this, "Shizuku is not running. Start Shizuku first.", Toast.LENGTH_LONG).show();
            return false;
        }

        if (!ShizukuBridge.hasPermission()) {
            requestShizukuPermission();
            Toast.makeText(this, "Grant Shizuku permission, then tap the button again.", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private void requestShizukuPermission() {
        if (!ShizukuBridge.isAvailable()) {
            Toast.makeText(this, "Shizuku is not running. Start it first.", Toast.LENGTH_LONG).show();
            openShizukuManager();
            return;
        }

        if (ShizukuBridge.hasPermission()) {
            Toast.makeText(this, "Shizuku permission already granted", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }

        boolean requested = ShizukuBridge.requestPermission();
        if (!requested) {
            Toast.makeText(this, operationError("Unable to request Shizuku permission"), Toast.LENGTH_LONG).show();
        }
    }

    private void openShizukuManager() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (intent == null) {
                Toast.makeText(this, "Shizuku is not installed", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(intent);
        } catch (Throwable error) {
            Toast.makeText(this, "Unable to open Shizuku", Toast.LENGTH_LONG).show();
        }
    }

    private String operationError(String prefix) {
        String detail = ShizukuBridge.getLastError();
        if (detail == null || detail.trim().isEmpty()) {
            return prefix + ". The setting could not be written or verified.";
        }
        return prefix + ": " + detail;
    }

    private void refreshStatus() {
        boolean shizukuRunning = ShizukuBridge.isAvailable();
        boolean shizukuGranted = shizukuRunning && ShizukuBridge.hasPermission();
        boolean featureEnabled = HyperBrightnessTileService.isEnabled(this);
        boolean autoEnabled = HyperBrightnessTileService.isAutomaticBrightnessEnabled(this);
        float adjustment = HyperBrightnessTileService.getCurrentAdjustment(this);

        statusText.setText(
                "Shizuku: " + (shizukuRunning ? "RUNNING" : "NOT RUNNING") +
                "\nShizuku permission: " + (shizukuGranted ? "GRANTED" : "NOT GRANTED") +
                "\nFeature: " + (featureEnabled ? "ON" : "OFF") +
                "\nSystem auto brightness: " + (autoEnabled ? "ON" : "OFF") +
                "\nCurrent auto-brightness adjustment: " + String.format(Locale.US, "%.2f", adjustment)
        );
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(6), 0, dp(6));
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
