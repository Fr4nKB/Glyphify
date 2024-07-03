package com.frank.glyphify.filehandling

import com.frank.glyphify.filehandling.Constants.LIGHT_DURATION_MS
import kotlin.math.pow

/**
 * A collection of light effects which are applied to a single beat
 */
object LightEffects {

    fun expDecay(beat: Pair<Int, Int>, slotsIn: Int, slotsOut: Int):
            List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val result = mutableListOf<Pair<Int, Int>>()

        for (i in slotsIn downTo 1) {
            val newTs = if(timestamp - LIGHT_DURATION_MS * i < 0) 0 else timestamp - LIGHT_DURATION_MS * i
            val newIntensity = (lightIntensity * (slotsIn - i).toDouble() / slotsIn).toInt()
            result.add(Pair(newTs, newIntensity))
        }

        result.add(beat)

        for (i in 1..slotsOut) {
            timestamp += LIGHT_DURATION_MS
            val newIntensity = (lightIntensity * (1 - (i.toDouble() / slotsOut).pow(2))).toInt()
            result.add(Pair(timestamp, newIntensity))
        }

        return result
    }

    fun fastBlink(beat: Pair<Int, Int>, slotsOut: Int):
            List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val result = mutableListOf<Pair<Int, Int>>()

        result.add(beat)

        for (i in 1..slotsOut) {
            timestamp += LIGHT_DURATION_MS
            val newIntensity = (lightIntensity * (slotsOut - i).toDouble() / slotsOut).toInt()
            result.add(Pair(timestamp, newIntensity))
        }

        return result
    }

    fun circusTent(beat: Pair<Int, Int>, slotsIn: Int): List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val tmp = mutableListOf<Pair<Int, Int>>()

        fun calculateIntensity(i: Int, slots: Int): Int {
            return (lightIntensity * (1 - (i.toDouble() / slots).pow(2))).toInt()
        }

        for (i in slotsIn downTo 1) {
            val newTs = if(timestamp - LIGHT_DURATION_MS * i < 0) 0 else timestamp - LIGHT_DURATION_MS * i
            tmp.add(Pair(newTs, calculateIntensity(i, slotsIn)))
        }

        tmp.add(beat)

        val decreaseSlots = slotsIn * 2 / 3
        for (i in 1..decreaseSlots) {
            timestamp += LIGHT_DURATION_MS
            tmp.add(Pair(timestamp, calculateIntensity(i, slotsIn)))
        }

        // mirror the light effect to have a symmetrical descent
        val result = mutableListOf<Pair<Int, Int>>()
        result.addAll(tmp)
        tmp.reverse()

        for ((t, l) in tmp) {
            timestamp += LIGHT_DURATION_MS
            result.add(Pair(timestamp, l))
        }

        return result
    }

    fun flickering(beat: Pair<Int, Int>, numFlickers: Int, slotsIn: Int):
            List<Pair<Int, Int>> {
        var (timestamp, lightIntensity) = beat
        val result = mutableListOf<Pair<Int, Int>>()

        for(i in 1..numFlickers) {
            result.addAll(fastBlink(Pair(timestamp, lightIntensity), slotsIn))
            timestamp += LIGHT_DURATION_MS * (slotsIn + 4)
        }

        return result
    }
}