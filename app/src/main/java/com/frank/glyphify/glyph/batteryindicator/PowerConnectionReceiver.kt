package com.frank.glyphify.glyph.batteryindicator

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver

class PowerConnectionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if(action == Intent.ACTION_POWER_CONNECTED) {
            val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
            serviceIntent.action = "POWER_ON"
            context.startService(serviceIntent)
        }
        else if(action == Intent.ACTION_POWER_DISCONNECTED) {
            val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
            serviceIntent.action = "POWER_OFF"
            context.startService(serviceIntent)
        }
    }
}