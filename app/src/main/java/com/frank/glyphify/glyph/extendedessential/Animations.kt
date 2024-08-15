package com.frank.glyphify.glyph.extendedessential

import com.nothing.ketchum.Common
import com.nothing.ketchum.GlyphFrame
import kotlin.math.ceil
import kotlin.math.floor

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

    private fun getVariableGlyphZones(glyphs: List<Int>): List<List<Int>> {
        val variableGlyphZones = MutableList(2) { mutableListOf<Int>() }
        if(Common.is22111()) {  // phone2 has 2 variable glyphs
            val (zone1, zone2) = glyphs.partition { it <= 18 }
            variableGlyphZones[0] = zone1.toMutableList()
            variableGlyphZones[1] = zone2.toMutableList()
        }
        // (1) and (2a) only have one variable glyph zone
        else variableGlyphZones[0] = glyphs.toMutableList()

        return variableGlyphZones
    }

    fun pingPongAnimation(frame: GlyphFrame.Builder, glyphs: List<Int>, time: Long, intensity: Int,
                             perStepDelay: Long): GlyphFrame.Builder {

        val variableGlyphZones = getVariableGlyphZones(glyphs)

        val speed = perStepDelay * 2
        var dynamicFrame = frame
        var polarGlyphIndex: Int
        var revertAnim: Boolean
        val baseIntensity = 0

        for(variableZone in variableGlyphZones) {
            val animDuration = variableZone.size * speed
            if(animDuration == 0L) continue

            val elapsedTick = ((time / speed) % variableZone.size).toInt()

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
                for(i in polarGlyphIndex downTo maxOf(polarGlyphIndex - 2, variableZone[0])) {
                    dynamicFrame.buildChannel(i, currIntensity)
                    currIntensity -= intensity / 2
                    currIntensity.coerceAtLeast(baseIntensity)
                }
            }
            else {
                for(i in polarGlyphIndex .. minOf(polarGlyphIndex + 2, variableZone.last())) {
                    dynamicFrame.buildChannel(i, currIntensity)
                    currIntensity -= intensity / 2
                    currIntensity.coerceAtLeast(baseIntensity)
                }
            }

        }

        return dynamicFrame
    }

    fun expansionAnimation(frame: GlyphFrame.Builder, glyphs: List<Int>, time: Long, intensity: Int,
                          perStepDelay: Long): GlyphFrame.Builder {

        val speed = perStepDelay * 2
        val variableGlyphZones = getVariableGlyphZones(glyphs)
        var dynamicFrame = frame
        var currIntensity: Int
        var phase2: Boolean

        for(variableZone in variableGlyphZones) {
            if(variableZone.isEmpty()) continue
            val glyphSize = variableZone.size
            val animDuration = glyphSize * speed

            val middle = (variableZone.first() + variableZone.last()) / 2.0
            val middlePoints = listOf(floor(middle).toInt(), ceil(middle).toInt())

            var elapsedTick = ((time / speed) % glyphSize).toInt()

            if((time / animDuration) % 2L == 0L) {
                phase2 = false
                currIntensity = intensity
            }
            else {
                phase2 = true
                currIntensity = 0
            }

            if(elapsedTick < glyphSize / 4 && !phase2) {
                currIntensity = (elapsedTick * intensity * 4 / glyphSize).coerceAtMost(intensity)
                dynamicFrame.buildChannel(middlePoints.first(), currIntensity)
                dynamicFrame.buildChannel(middlePoints.last(), currIntensity)
                continue
            }
            else if(elapsedTick >= glyphSize * 3 / 4 && phase2) {
                if(elapsedTick == glyphSize - 1) currIntensity = 0
                else currIntensity = (intensity * (1 - elapsedTick * 1.0 / glyphSize)).toInt()

                for(index in variableZone.first() + 1..< variableZone.last()) {
                    dynamicFrame.buildChannel(index, 0)
                }
                dynamicFrame.buildChannel(variableZone.first(), currIntensity)
                dynamicFrame.buildChannel(variableZone.last(), currIntensity)
                continue
            }

            for(index in middlePoints.first() downTo middlePoints.first() - elapsedTick / 2) {
                dynamicFrame.buildChannel(index, currIntensity)
            }

            for(index in middlePoints.last()..middlePoints.last() + elapsedTick / 2) {
                dynamicFrame.buildChannel(index, currIntensity)
            }

        }

        return dynamicFrame
    }
}