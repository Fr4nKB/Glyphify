package com.frank.glyphify.glyph
import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

object BeatDetector {

    /**
     * Given a path to a wav file it returns a 2D array of beats: each beat is a
     * pair (timestamp, energy), beats are grouped into high-freq-right-ch, high-freq-low-ch,
     * low-freq-mono, high-freq-mono and low-freq-mono (yes, again)
     * @param context: app context
     * @param filepath: path to wav file in app's filesystem
     * @return 2D array containing the beats
     * */
    fun detectBeatsAndFrequencies(context: Context, filepath: String, filename: String):
            Pair<List<Double>, List<List<Pair<Int, Double>>>> {
        // this method uses Chaquopy to execute python code and the librosa which is a python library
        // that let us extract beats from a tune
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }

        val python = Python.getInstance()
        val pythonModule = python.getModule("beatDetectorFun")

        val result: PyObject = pythonModule.callAttr("detect_beats_and_frequencies", filepath, filename)

        val tempos = listOf(result.asList()[0].toDouble(), result.asList()[1].toDouble())

        val allBeats: MutableList<MutableList<Pair<Int, Double>>> = mutableListOf()

        for (band in result.asList()[2].asList()) {
            val beatsBand: MutableList<Pair<Int, Double>> = mutableListOf()
            for (beat in band.asList()) {
                val time = beat.asList()[0].toInt()
                val energy = beat.asList()[1].toDouble()
                beatsBand.add(Pair(time, energy))
            }
            allBeats.add(beatsBand)
        }

        return Pair(tempos, allBeats)
    }
}