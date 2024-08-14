package com.frank.glyphify.glyph.extendedessential

import com.nothing.ketchum.Common
import com.nothing.ketchum.GlyphFrame

object Animations {

    fun pulseAnimation(frame: GlyphFrame.Builder, glyphs: List<Int>, time: Long, intensity: Int,
                       stepSize: Int, perStepDelay: Long): GlyphFrame.Builder {

        var dynamicFrame = frame
        val cycleDuration = 5000L // 5000ms cycle duration to ensure 4000ms between animations

        val currentTick = time % cycleDuration

        val light = maxOf(intensity - stepSize * (currentTick / perStepDelay).toInt(), 0)
        for (glyph in glyphs) {
            dynamicFrame = dynamicFrame.buildChannel(glyph, light)
        }

        return dynamicFrame
    }

    fun pingPongAnimation(frame: GlyphFrame.Builder, glyphs: List<Int>, time: Long, intensity: Int,
                             perStepDelay: Long): GlyphFrame.Builder {

        val variableGlyphZones = MutableList(2) { mutableListOf<Int>() }
        if(Common.is22111()) {  // phone2 has 2 variable glyphs
            val (zone1, zone2) = glyphs.partition { it <= 18 }
            variableGlyphZones[0] = zone1.toMutableList()
            variableGlyphZones[1] = zone2.toMutableList()
        }
        // (1) and (2a) only have one variable glyph zone
        else variableGlyphZones[0] = glyphs.toMutableList()

        var dynamicFrame = frame
        var polarGlyphIndex: Int
        var revertAnim: Boolean
        val baseIntensity = 0

        for(variableZone in variableGlyphZones) {
            val animDuration = variableZone.size * perStepDelay * 2
            if(animDuration == 0L) continue

            val elapsedTick = ((time / (perStepDelay * 2)) % variableZone.size).toInt()

            if((time / animDuration) % 2L == 0L) {
                revertAnim = false
                polarGlyphIndex = variableZone[0] + elapsedTick
            }
            else {
                revertAnim = true
                polarGlyphIndex = variableZone[0] + (variableZone.size - 1) - elapsedTick
            }

            for(glyph in variableZone) {
                dynamicFrame = dynamicFrame.buildChannel(glyph, baseIntensity)
            }

            var currIntensity = intensity
            if(!revertAnim) {
                for (i in polarGlyphIndex downTo maxOf(polarGlyphIndex - 2, variableZone[0])) {
                    dynamicFrame.buildChannel(i, currIntensity)
                    currIntensity -= intensity / 2
                    currIntensity.coerceAtLeast(baseIntensity)
                }
            }
            else {
                for (i in polarGlyphIndex .. minOf(polarGlyphIndex + 2, variableZone.last())) {
                    dynamicFrame.buildChannel(i, currIntensity)
                    currIntensity -= intensity / 2
                    currIntensity.coerceAtLeast(baseIntensity)
                }
            }

        }

        return dynamicFrame
    }
}