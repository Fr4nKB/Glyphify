package com.frank.glyphify.glyph.notificationmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class ExtendedEssentialReceiver : BroadcastReceiver() {
    private var isPhoneLocked = false

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, ExtendedEssentialService::class.java)

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                isPhoneLocked = true
                serviceIntent.action = "PHONE_LOCKED"
                context.startService(serviceIntent)
            }
            Intent.ACTION_USER_PRESENT -> {
                isPhoneLocked = false
                serviceIntent.action = "PHONE_UNLOCKED"
                context.startService(serviceIntent)
            }
        }
    }

    fun isPhoneLocked() = isPhoneLocked
}

