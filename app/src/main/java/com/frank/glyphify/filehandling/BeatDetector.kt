package com.frank.glyphify.filehandling
import android.util.Log
import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

object BeatDetector {
    fun detectBeatsAndFrequencies(context: Context, filepath: String): List<List<Pair<Int, Double>>> {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }

        val python = Python.getInstance()
        val pythonModule = python.getModule("beatDetectorFun")

        val result: PyObject = pythonModule.callAttr("detect_beats_and_frequencies", filepath)

        val allBeats: MutableList<MutableList<Pair<Int, Double>>> = mutableListOf()

        for (band in result.asList()) {
            val beatsBand: MutableList<Pair<Int, Double>> = mutableListOf()
            for (beat in band.asList()) {
                val time = beat.asList()[0].toInt()
                val energy = beat.asList()[1].toDouble()
                beatsBand.add(Pair(time, energy))
            }
            allBeats.add(beatsBand)
        }

        return allBeats
    }
}