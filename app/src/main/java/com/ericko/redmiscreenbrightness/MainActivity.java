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
        refreshStatus();
        if (Settings.System.canWrite(this)) {
            BackgroundBrightnessService.start(this);
        }
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
        title.setText("Redmi Screen Brightness");
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

        addBrightnessButton(root, "20% - raw 11", 20, 11);
        addBrightnessButton(root, "30% - raw 17", 30, 17);
        addBrightnessButton(root, "40% - raw 26", 40, 26);
        addBrightnessButton(root, "50% - raw 38", 50, 38);
        addBrightnessButton(root, "60% - raw 49", 60, 49);

        TextView note = new TextView(this);
        note.setText("Sensor policy:\nNORMAL does not auto-adjust.\nTOO_DARK applies 20%.\nTOO_BRIGHT applies 60%.\nReturning from either state to NORMAL applies 30% once.");
        note.setTextSize(14);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, fullWidth());

        return scrollView;
    }

    private void addBrightnessButton(LinearLayout root, String label, int percent, int raw) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> {
            boolean ok = HyperBrightnessTileService.applyBrightness(this, percent, raw);
            Toast.makeText(this, ok ? "Applied " + label : "Grant modify system settings first", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });
        root.addView(button, fullWidth());
    }

    private void openWriteSettingsPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void refreshStatus() {
        boolean canWrite = Settings.System.canWrite(this);
        statusText.setText(canWrite
                ? "Permission granted. Background light sensor service is enabled."
                : "Permission missing. Grant modify system settings before brightness can be changed.");
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
