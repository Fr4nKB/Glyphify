package com.frank.glyphify

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

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
}

