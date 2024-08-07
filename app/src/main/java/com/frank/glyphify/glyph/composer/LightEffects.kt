package com.frank.glyphify.glyph.composer

import com.frank.glyphify.Constants.LIGHT_DURATION_US
import kotlin.math.pow

/**
 * A collection of light effects which are applied to a single beat
 */
object LightEffects {

    private fun calculateBase(tempo: Double, base: Int, coeff: Double, range: IntRange): Int {
        return (base - coeff * tempo).toInt().coerceIn(range)
    }


    fun expDecay(beat: Pair<Int, Int>, tempo: Double, offsetSlotsOut: Int = 0):
            List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val result = mutableListOf<Pair<Int, Int>>()

        val slotsOut = calculateBase(tempo, 23, 7.0/60, 4..16)

        for (i in 2 downTo 1) {
            val newTs = if(timestamp - LIGHT_DURATION_US * i < 0) 0 else timestamp - LIGHT_DURATION_US * i
            val newIntensity = (lightIntensity * (2 - i).toDouble() / 2).toInt()
            result.add(Pair(newTs, newIntensity))
        }

        result.add(Pair(timestamp, lightIntensity))

        for (i in 1..slotsOut) {
            timestamp += LIGHT_DURATION_US
            val newIntensity = (lightIntensity * (1 - (i.toDouble() / slotsOut).pow(2))).toInt()
            result.add(Pair(timestamp, newIntensity))
        }

        return result
    }

    fun fastBlink(beat: Pair<Int, Int>, tempo: Double, offsetSlotsOut: Int = 0):
            List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val result = mutableListOf<Pair<Int, Int>>()

        result.add(Pair(timestamp, lightIntensity))

        val slotsOut = calculateBase(tempo, 11, 1.0/20, 4..8)

        for (i in 1..slotsOut) {
            timestamp += LIGHT_DURATION_US
            val newIntensity = (lightIntensity * (slotsOut - i).toDouble() / slotsOut).toInt()
            result.add(Pair(timestamp, newIntensity))
        }

        return result
    }

    fun circusTent(beat: Pair<Int, Int>, tempo: Double, offsetSlotsIn: Int = 0):
            List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val tmp = mutableListOf<Pair<Int, Int>>()

        fun calculateIntensity(i: Int, slots: Int): Int {
            return (lightIntensity * (1 - (i.toDouble() / slots).pow(2))).toInt()
        }


        val slotsIn = calculateBase(tempo, 14, 1.0/15, 4..10)

        for (i in slotsIn downTo 1) {
            val newTs = if(timestamp - LIGHT_DURATION_US * i < 0) 0 else timestamp - LIGHT_DURATION_US * i
            tmp.add(Pair(newTs, calculateIntensity(i, slotsIn)))
        }

        tmp.add(Pair(timestamp, lightIntensity))

        val decreaseSlots = slotsIn * 2 / 3
        for (i in 1..decreaseSlots) {
            timestamp += LIGHT_DURATION_US
            tmp.add(Pair(timestamp, calculateIntensity(i, slotsIn)))
        }

        // mirror the light effect to have a symmetrical descent
        val result = mutableListOf<Pair<Int, Int>>()
        result.addAll(tmp)
        tmp.reverse()

        for ((t, l) in tmp) {
            timestamp += LIGHT_DURATION_US
            result.add(Pair(timestamp, l))
        }

        return result
    }

    fun flickering(beat: Pair<Int, Int>, tempo: Double, numFlickers: Int,
                   offsetSlotsIn: Int = 0): List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val result = mutableListOf<Pair<Int, Int>>()

        val slotsOut = calculateBase(tempo, 11, 1.0/20, 4..8)

        for(i in 1..numFlickers) {
            result.addAll(fastBlink(Pair(timestamp, lightIntensity), tempo))
            timestamp += LIGHT_DURATION_US * (slotsOut + 4)
        }

        return result
    }
}