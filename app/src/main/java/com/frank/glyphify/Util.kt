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
    fun loadPreferences(context: Context, numZones: Int): MutableList<Triple<Int, List<BigInteger>, Boolean>> {
        val sharedPref: SharedPreferences =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("contactsMapping", null)
        val glyphsMapping = MutableList(numZones) { Triple(-1, emptyList<BigInteger>(), false) }

        if (jsonString != null) {
            val mapping = JSONObject(jsonString)
            for (i in 0 until numZones) {
                if (mapping.has(i.toString())) {
                    val jsonArray = mapping.getJSONArray(i.toString())
                    val glyphId = jsonArray.getInt(0)
                    val pulse = jsonArray.getBoolean(2)

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

    /**
     * Used to pass from a simplified glyph addressing to the Nothing's glyph addressing
     * @param index: simplified index for glyph zones
     * @return list of ints containing the actual glyph zones
     * */
    fun getGlyphMapping(index: Int): List<Int> {
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
            4 -> {
                if(Common.is20111()) {
                    glyphIndexes = listOf(Glyph.Code_20111.E1)
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

