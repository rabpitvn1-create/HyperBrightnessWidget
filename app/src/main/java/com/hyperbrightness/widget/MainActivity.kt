package com.hyperbrightness.widget

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var cycleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildLayout(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 64)
        }

        root.addView(TextView(this).apply {
            text = "Hyper Brightness Widget"
            textSize = 24f
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "RAW baseline only. No lux curve, no sensor policy, no learning, no AI."
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }, matchWrap())

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 24, 0, 24)
        }
        root.addView(statusText, matchWrap())

        root.addView(button("Grant WRITE_SETTINGS") {
            startActivity(BrightnessController.writeSettingsIntent(this))
        })

        cycleButton = button("Apply next raw level") {
            cycleRaw()
        }
        root.addView(cycleButton)

        return ScrollView(this).apply { addView(root) }
    }

    private fun cycleRaw() {
        if (!BrightnessController.canWriteSettings(this)) {
            startActivity(BrightnessController.writeSettingsIntent(this))
            return
        }
        val current = BrightnessController.getRawBrightness(this)
        val next = BrightnessLevels.nextAfter(current)
        val ok = BrightnessController.writeRawBrightness(this, next)
        Toast.makeText(this, "Set raw $next: ${if (ok) "OK" else "check manually"}", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun refreshStatus() {
        val canWrite = BrightnessController.canWriteSettings(this)
        val raw = BrightnessController.getRawBrightness(this)
        cycleButton.isEnabled = canWrite
        statusText.text = buildString {
            appendLine("WRITE_SETTINGS: ${if (canWrite) "granted" else "not granted"}")
            appendLine("Current raw: $raw")
            appendLine("Raw levels: ${BrightnessLevels.RAW_LEVELS.joinToString()}")
            appendLine("Hard cap: ${BrightnessLevels.MAX_RAW}")
        }
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
