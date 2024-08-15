package com.frank.glyphify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.frank.glyphify.glyph.extendedessential.ExtendedEssentialService
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.Calendar

object Util {
    fun loadPreferences(context: Context, numZones: Int): MutableList<Triple<Int, List<BigInteger>, Int>> {
        val sharedPref: SharedPreferences =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("contactsMapping", null)
        val glyphsMapping = MutableList(numZones) { Triple(-1, emptyList<BigInteger>(), 0) }

        if (jsonString != null) {
            val mapping = JSONObject(jsonString)
            for (i in 0 until numZones) {
                if (mapping.has(i.toString())) {
                    val jsonArray = mapping.getJSONArray(i.toString())
                    val glyphId = jsonArray.getInt(0)

                    var pulse: Int
                    try {
                        pulse = jsonArray.getInt(2)
                    }
                    catch (e: Exception) {
                        pulse = if (jsonArray.getBoolean(2)) 1 else 0
                    }

                    var mappingIdsJsonArray = JSONArray()
                    try {
                        mappingIdsJsonArray = jsonArray.getJSONArray(1)
                    } catch (_: Exception) {}

                    val mappingIds = mutableListOf<BigInteger>()
                    for (j in 0 until mappingIdsJsonArray.length()) {
                        mappingIds.add(BigInteger(mappingIdsJsonArray.getString(j)))
                    }

                    glyphsMapping[i] = Triple(glyphId, mappingIds.toList(), pulse)
                }
            }
        }

        return glyphsMapping
    }

    fun resizeDrawable(resources: Resources, drawable: Drawable, width: Int, height: Int): Drawable {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    fun fromStringToNum(packageName: String): BigInteger {
        return -BigInteger(packageName.toByteArray())
    }

    fun fromNumToString(appId: BigInteger): String {
        return String((-appId).toByteArray())
    }

    fun exactAlarm(context: Context, intentAction: String, setAction: Int) {
        val sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        var time = ""
        if(intentAction == "SLEEP_ON") time = sharedPref.getString("sleepStart", "") ?: ""
        else if(intentAction == "SLEEP_OFF") time = sharedPref.getString("sleepEnd", "") ?: ""

        if (time.isNotEmpty()) {
            val timeParts = time.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExtendedEssentialService::class.java).apply {
                action = intentAction
            }
            val pendingIntent = PendingIntent.getService(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            alarmManager.cancel(pendingIntent)
            if(setAction in 1..2) {
                val alarmTime: Long
                if(setAction == 1) alarmTime = calendar.timeInMillis
                else alarmTime = calendar.timeInMillis + AlarmManager.INTERVAL_DAY

                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            }
        }
    }

}

