package com.hyperbrightness.widget

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var permissionStatus: TextView
    private lateinit var rawStatus: TextView
    private lateinit var applyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionStatus = TextView(this)
        rawStatus = TextView(this)
        applyButton = Button(this)

        val title = TextView(this).apply {
            text = "Hyper Brightness Widget"
            textSize = 22f
            gravity = Gravity.START
        }

        val scope = TextView(this).apply {
            text = "RAW baseline only. No lux curve, no sensor policy, no learning, no AI."
            textSize = 15f
        }

        val permissionButton = Button(this).apply {
            text = "Grant WRITE_SETTINGS"
            setOnClickListener { startActivity(BrightnessController.writeSettingsIntent(this@MainActivity)) }
        }

        applyButton.apply {
            text = "Apply next raw level"
            setOnClickListener {
                val current = BrightnessController.getRawBrightness(this@MainActivity)
                val next = BrightnessLevels.nextAfter(current)
                BrightnessController.writeRawBrightness(this@MainActivity, next)
                refreshStatus()
            }
        }

        val levels = TextView(this).apply {
            text = "Levels: ${BrightnessLevels.RAW_LEVELS.joinToString()} | hard cap: ${BrightnessLevels.MAX_RAW}"
            textSize = 15f
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            addView(title)
            addView(scope, itemParams())
            addView(permissionStatus, itemParams())
            addView(rawStatus, itemParams())
            addView(levels, itemParams())
            addView(permissionButton, itemParams())
            addView(applyButton, itemParams())
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val canWrite = BrightnessController.canWriteSettings(this)
        val raw = BrightnessController.getRawBrightness(this)
        permissionStatus.text = if (canWrite) "WRITE_SETTINGS: granted" else "WRITE_SETTINGS: not granted"
        rawStatus.text = "Current raw brightness: $raw"
        applyButton.isEnabled = canWrite
    }

    private fun itemParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
