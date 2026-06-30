package com.hyperbrightness.widget

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
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

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 32, 0, 32)
        }
        root.addView(statusText, matchWrap())

        root.addView(button("Cấp quyền WRITE_SETTINGS") {
            startActivity(BrightnessController.writeSettingsIntent(this))
        })

        BrightnessLevels.RAW_LEVELS.forEach { raw ->
            root.addView(button("Set raw $raw") { setRaw(raw) })
        }

        root.addView(button("Cycle raw") {
            val next = BrightnessLevels.nextAfter(BrightnessController.getRawBrightness(this))
            setRaw(next)
        })

        root.addView(button("Cập nhật trạng thái") {
            refreshStatus()
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun setRaw(raw: Int) {
        if (!BrightnessController.canWriteSettings(this)) {
            startActivity(BrightnessController.writeSettingsIntent(this))
            return
        }
        val ok = BrightnessController.writeRawBrightness(this, raw)
        toast("Set raw $raw: ${if (ok) "OK" else "lỗi/lệch"}")
        refreshStatus()
    }

    private fun refreshStatus() {
        statusText.text = buildString {
            appendLine("Stage: 0 - clean raw foundation")
            appendLine("WRITE_SETTINGS: ${BrightnessController.canWriteSettings(this@MainActivity)}")
            appendLine("System raw: ${BrightnessController.getRawBrightness(this@MainActivity)}")
            appendLine("Valid raw levels: ${BrightnessLevels.RAW_LEVELS.joinToString()}")
            appendLine("Hard cap: ${BrightnessLevels.MAX_RAW}")
            appendLine()
            appendLine("Lux: chưa dùng")
            appendLine("Sensor: chưa dùng")
            appendLine("Auto brightness: chưa dùng")
            appendLine("Learning: chưa dùng")
        }
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
