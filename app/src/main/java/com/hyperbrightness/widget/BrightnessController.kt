package com.hyperbrightness.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object BrightnessController {
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setManualBrightnessMode(context: Context) {
        if (!canWriteSettings(context)) return
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
    }

    fun getRawBrightness(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(BrightnessLevels.MIN_RAW)

    fun writeRawBrightness(context: Context, requestedRaw: Int): Boolean {
        if (!canWriteSettings(context)) return false
        val raw = BrightnessLevels.clamp(requestedRaw)
        setManualBrightnessMode(context)
        val ok = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
        val after = getRawBrightness(context)
        return ok && kotlin.math.abs(after - raw) <= 1
    }
}
