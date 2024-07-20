package com.frank.glyphify

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Util {
    fun loadPreferences(context: Context, numZones: Int): MutableList<Triple<Int, Long, Boolean>> {
        val sharedPref: SharedPreferences =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("contactsMapping", null)
        val contacts2glyphMapping = MutableList(numZones) { Triple(-1, -1L, false) }

        if (jsonString != null) {
            val mapping = JSONObject(jsonString)
            for (i in 0 until numZones) {
                if (mapping.has(i.toString())) {
                    val jsonArray = mapping.getJSONArray(i.toString())
                    val glyphId = jsonArray.getInt(0)
                    val contactId = jsonArray.getLong(1)
                    val pulse = jsonArray.getBoolean(2)
                    contacts2glyphMapping[i] = Triple(glyphId, contactId, pulse)
                }

            }
        }

        return contacts2glyphMapping
    }
}