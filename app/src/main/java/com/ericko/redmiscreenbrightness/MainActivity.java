package com.ericko.redmiscreenbrightness;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
        if (Settings.System.canWrite(this) && HyperBrightnessTileService.isEnabled(this)) {
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

        Button permissionButton = new Button(this);
        permissionButton.setText("Grant modify system settings");
        permissionButton.setOnClickListener(v -> openWriteSettingsPermission());
        root.addView(permissionButton, fullWidth());

        Button enableButton = new Button(this);
        enableButton.setText("Enable Auto -20%");
        enableButton.setOnClickListener(v -> {
            boolean ok = HyperBrightnessTileService.setEnabled(this, true);
            Toast.makeText(
                    this,
                    ok ? "Auto brightness enabled with -20% bias" : "Grant modify system settings first",
                    Toast.LENGTH_SHORT
            ).show();
            refreshStatus();
        });
        root.addView(enableButton, fullWidth());

        Button disableButton = new Button(this);
        disableButton.setText("Disable -20% bias");
        disableButton.setOnClickListener(v -> {
            boolean ok = HyperBrightnessTileService.setEnabled(this, false);
            Toast.makeText(
                    this,
                    ok ? "Previous auto-brightness adjustment restored" : "Grant modify system settings first",
                    Toast.LENGTH_SHORT
            ).show();
            refreshStatus();
        });
        root.addView(disableButton, fullWidth());

        TextView note = new TextView(this);
        note.setText(
                "How it works:\n" +
                "• Android/HyperOS keeps Automatic brightness enabled.\n" +
                "• The app no longer reads the ambient-light sensor or chooses its own brightness levels.\n" +
                "• It applies screen_auto_brightness_adj = -0.20.\n" +
                "• Disabling the feature restores the adjustment value that existed before enabling it.\n\n" +
                "Note: -0.20 is Android's auto-brightness bias value. It is not guaranteed to equal an exact 20% reduction in physical panel luminance."
        );
        note.setTextSize(14);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, fullWidth());

        return scrollView;
    }

    private void openWriteSettingsPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void refreshStatus() {
        boolean canWrite = Settings.System.canWrite(this);
        if (!canWrite) {
            statusText.setText("Permission missing. Grant modify system settings first.");
            return;
        }

        boolean featureEnabled = HyperBrightnessTileService.isEnabled(this);
        boolean autoEnabled = HyperBrightnessTileService.isAutomaticBrightnessEnabled(this);
        float adjustment = HyperBrightnessTileService.getCurrentAdjustment(this);

        statusText.setText(
                "Feature: " + (featureEnabled ? "ON" : "OFF") +
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
