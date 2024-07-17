package com.frank.glyphify.glyph.batteryindicator

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver

class PowerConnectionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // dedicate to Phone(2a) users
        if (action == Intent.ACTION_POWER_CONNECTED) {
            val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
            context.startForegroundService(serviceIntent)
        }
        else if (action == Intent.ACTION_POWER_DISCONNECTED) {
            val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
            context.stopService(serviceIntent)
        }
    }
}