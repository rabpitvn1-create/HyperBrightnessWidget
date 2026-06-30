package com.hyperbrightness.widget

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class HyperBrightnessTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (BrightnessController.canWriteSettings(this)) {
            val next = BrightnessLevels.nextAfter(BrightnessController.getRawBrightness(this))
            BrightnessController.writeRawBrightness(this, next)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(BrightnessController.writeSettingsIntent(this))
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            tile.state = if (BrightnessController.canWriteSettings(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "raw ${BrightnessController.getRawBrightness(this)}"
            }
            tile.updateTile()
        }
    }
}
