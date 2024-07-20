package com.frank.glyphify.glyph.notificationmanager

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.PowerManager
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.frank.glyphify.Constants.CHANNEL_ID
import com.frank.glyphify.Constants.GLYPH_DEFAULT_INTESITY
import com.frank.glyphify.R
import com.frank.glyphify.Util.loadPreferences
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import kotlinx.coroutines.*
import org.json.JSONObject

class GlyphNotificationManagerService: NotificationListenerService() {
    private lateinit var mGM: GlyphManager
    private var mCallback: GlyphManager.Callback? = null
    private var numZones: Int = 0
    private lateinit var contacts2glyphMapping: MutableList<Triple<Int, Long, Boolean>>
    private var activeZonesStatic = mutableListOf<Int>()
    private lateinit var frameStatic: GlyphFrame.Builder
    private var activeZonesPulse = mutableListOf<Int>()
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var serviceJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    private fun getGlyphMapping(index: Int): List<Int> {
        var glyphIndexes: List<Int>
        when(index) {
            2 -> {
                if(Common.is20111()) {
                    glyphIndexes = (Glyph.Code_20111.C1..Glyph.Code_20111.C4).toList()
                }
                else if(Common.is23111()) {
                    glyphIndexes = (Glyph.Code_23111.C_1..Glyph.Code_23111.C_24).toList()
                }
                else glyphIndexes = listOf(index)
            }
            3 -> {
                if(Common.is20111()) {
                    glyphIndexes = (Glyph.Code_20111.D1_1..Glyph.Code_20111.D1_8).toList()
                }
                else if(Common.is22111()) {
                    glyphIndexes = (Glyph.Code_22111.C1_1..Glyph.Code_22111.C1_16).toList()
                }
                else glyphIndexes = listOf(index)
            }
            9 -> {
                glyphIndexes = (Glyph.Code_22111.D1_1..Glyph.Code_22111.D1_8).toList()
            }
            else -> glyphIndexes = listOf(index)
        }

        return glyphIndexes
    }

    private fun extractContactIdFromNotification(sbn: StatusBarNotification): Triple<Int, Long, Boolean>? {
        val packageName = sbn.packageName
        val name = sbn.notification.extras.getString(Notification.EXTRA_TITLE)

        // Check if the notification is from a messaging app
        if (packageName !in listOf(
                "com.google.android.apps.messaging", "com.whatsapp", "org.telegram.messenger")) {
            return null
        }

        if(name == null || name in listOf("WhatsApp") || name in listOf("Telegram")) return null

        // retrieve contactId using the name
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME),
            ContactsContract.Contacts.DISPLAY_NAME + " = ?",
            arrayOf(name),    // contact name
            null
        )

        var mappingTriple: Triple<Int, Long, Boolean>? = null

        if (cursor != null) {
            val contactIdIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)

            while (cursor.moveToNext() && mappingTriple == null) {
                val contactId = cursor.getLong(contactIdIndex)
                mappingTriple = (contacts2glyphMapping as List<Triple<Int, Long, Boolean>>)
                    .find { it.second == contactId }
            }

            cursor.close()
        }

        return mappingTriple
    }

    private fun showGlyphNotifications() {
        var framePulse: GlyphFrame.Builder

        frameStatic = mGM.glyphFrameBuilder
        for(zone in activeZonesStatic) {
            frameStatic = frameStatic.buildChannel(zone)
        }

        wakeLock.acquire( 10 * 1000)
        if(activeZonesPulse.size > 0 && (serviceJob?.isActive != true)) {
            serviceJob = serviceScope.launch {
                while (isActive) {
                    for(light in GLYPH_DEFAULT_INTESITY downTo 0 step 100) {    //  gradually decrease light intensity
                        // at each iteration use either frameStatic with fixed light intensity or the
                        // "clean" builder
                        framePulse = frameStatic
                        for(zone in activeZonesPulse) {
                            framePulse = framePulse.buildChannel(zone, light)
                        }

                        mGM.toggle(framePulse.build())
                        delay(25)
                    }
                    delay(3000)
                }
            }
        }
        else {
            mGM.toggle(frameStatic.build())
        }
        wakeLock.release()
    }

    override fun onCreate() {
        super.onCreate()

        init()
        val localGM = GlyphManager.getInstance(applicationContext)
        localGM?.init(mCallback)
        mGM = localGM
        mGM.turnOff()

        if(Common.is20111()) numZones = 5
        else if (Common.is22111()) numZones = 11
        else if (Common.is23111()) numZones = 3
        contacts2glyphMapping = loadPreferences(this, numZones)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Glyhpify::NotificationListener"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val contactId = extractContactIdFromNotification(sbn) ?: return

        val newZones = getGlyphMapping(contactId.first)
        if(contactId.third) {
            newZones.filter { it !in activeZonesPulse }.forEach { activeZonesPulse.add(it) }
        }
        else {
            newZones.filter { it !in activeZonesStatic }.forEach { activeZonesStatic.add(it) }
        }

        showGlyphNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if(sbn != null) {
            val contactId = extractContactIdFromNotification(sbn) ?: return

            val newZones = getGlyphMapping(contactId.first)

            activeZonesPulse.removeAll(newZones)
            activeZonesStatic.removeAll(newZones)

            showGlyphNotifications()
        }
    }

    override fun onDestroy() {
        try {
            mGM.turnOff()
            mGM.closeSession()
        }
        catch (e: GlyphException) {
            e.printStackTrace()
        }
        mGM.unInit()

        serviceScope.cancel()
        super.onDestroy()
    }

    private fun init() {
        mCallback = object : GlyphManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                if (Common.is20111()) mGM.register(Common.DEVICE_20111)
                if (Common.is22111()) mGM.register(Common.DEVICE_22111)
                if (Common.is23111()) mGM.register(Common.DEVICE_23111)

                try {
                    mGM.openSession()
                }
                catch (e: GlyphException) {
                    e.printStackTrace()
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                mGM.closeSession()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        contacts2glyphMapping = loadPreferences(this, numZones)

        return START_STICKY
    }
}