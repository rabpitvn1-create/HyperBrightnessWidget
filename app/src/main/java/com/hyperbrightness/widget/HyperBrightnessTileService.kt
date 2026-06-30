package com.hyperbrightness.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class HyperBrightnessTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!BrightnessController.canWriteSettings(this)) {
            openWriteSettings()
            refreshTile()
            return
        }

        val current = BrightnessController.getRawBrightness(this)
        val next = BrightnessLevels.nextAfter(current)
        BrightnessController.writeRawBrightness(this, next)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val canWrite = BrightnessController.canWriteSettings(this)
        val raw = BrightnessController.getRawBrightness(this)

        tile.label = if (canWrite) "Raw $raw" else getString(R.string.tile_label)
        tile.state = if (canWrite) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (canWrite) "Tap: next raw" else "Grant permission"
        }
        tile.updateTile()
    }

    private fun openWriteSettings() {
        val intent = BrightnessController.writeSettingsIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
