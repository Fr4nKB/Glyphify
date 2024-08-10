package com.frank.glyphify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID
import com.frank.glyphify.glyph.batteryindicator.BatteryIndicatorService
import java.util.concurrent.TimeUnit

class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if(action == Intent.ACTION_BOOT_COMPLETED) {

            if(Build.MODEL == PHONE2A_MODEL_ID) {
                val serviceIntent = Intent(context, BatteryIndicatorService::class.java)
                context.startService(serviceIntent)
            }

            val singleWorkReq = OneTimeWorkRequestBuilder<AppUpdater>()
                .build()
            WorkManager.getInstance(context).enqueue(singleWorkReq)

            val periodicWorkRequest = PeriodicWorkRequest.Builder(AppUpdater::class.java, 4, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueue(periodicWorkRequest)
        }

    }
}