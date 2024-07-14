package com.frank.glyphify.glyph.batteryindicator

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.os.Build
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID

class PowerConnectionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val model = Build.MODEL

        // dedicate to Phone(2a) users
        if (action == Intent.ACTION_POWER_CONNECTED
            && model == PHONE2A_MODEL_ID) {
            val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
            context.startForegroundService(serviceIntent)
        }
        else if (action == Intent.ACTION_POWER_DISCONNECTED
            && model == PHONE2A_MODEL_ID) {
            val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
            context.stopService(serviceIntent)
        }
    }
}