package com.frank.glyphify.glyph.batteryindicator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID

class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if(action == Intent.ACTION_BOOT_COMPLETED
            && Build.MODEL == PHONE2A_MODEL_ID) {
            val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
            context.startService(serviceIntent)
        }
    }
}