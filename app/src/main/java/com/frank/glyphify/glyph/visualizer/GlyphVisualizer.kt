package com.frank.glyphify.glyph.visualizer

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import com.frank.glyphify.glyph.composer.FileHandling.decodeAndDecompress
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphManager

class GlyphVisualizer(private var context: Context): AutoCloseable {
    private var mGM: GlyphManager? = null
    private var mCallback: GlyphManager.Callback? = null

    private var numZones: Int = 5
    private var exitFlag: Boolean = false

    init {
        init()
        val localGM = GlyphManager.getInstance(context)
        localGM?.init(mCallback)
        mGM = localGM
    }

    private fun init() {
        mCallback = object : GlyphManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                if (Common.is20111()) mGM?.register(Common.DEVICE_20111)
                if (Common.is22111()) mGM?.register(Common.DEVICE_22111)
                if (Common.is23111()) mGM?.register(Common.DEVICE_23111)
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                mGM?.turnOff()
                mGM?.closeSession()
            }
        }
    }

    private fun getGlyphMapping(index: Int,): List<Int> {
        var glyphIndexes: List<Int>
        when(index) {
            0 -> {
                if(Common.is22111()) {
                    glyphIndexes = (Glyph.Code_22111.A1..Glyph.Code_22111.A2).toList()
                }
                else glyphIndexes = listOf(index)
            }
            1 -> {
                if(Common.is22111()) {
                    glyphIndexes = listOf(Glyph.Code_22111.B1)
                }
                else glyphIndexes = listOf(index)
            }
            2 -> {
                if(Common.is20111()) {
                    glyphIndexes = (Glyph.Code_20111.C1..Glyph.Code_20111.C4).toList()
                }
                else if(Common.is22111()) {
                    glyphIndexes = (Glyph.Code_22111.C1_1..Glyph.Code_22111.C6).toList()
                }
                else glyphIndexes = listOf(index)
            }
            3 -> {
                if(Common.is20111()) {
                    glyphIndexes = (Glyph.Code_20111.D1_1..Glyph.Code_20111.D1_8).toList()
                }
                else if(Common.is22111()) {
                    glyphIndexes = (Glyph.Code_22111.D1_1..Glyph.Code_22111.D1_8).toList()
                }
                else glyphIndexes = listOf(index)
            }
            4 -> {
                if(Common.is20111()) {
                    glyphIndexes = listOf(Glyph.Code_20111.E1)
                }
                else if(Common.is22111()) {
                    glyphIndexes = listOf(Glyph.Code_22111.E1)
                }
                else glyphIndexes = listOf(index)
            }
            else -> glyphIndexes = listOf(index)
        }

        return glyphIndexes
    }

    private fun getAuthorDataFromFile(filePath: String): String {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val commpressedEncodedAuthorTag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR) ?: "Unknown"
        retriever.release()

        return decodeAndDecompress(commpressedEncodedAuthorTag)
    }

    fun startVisualization(filePath: String, onFinished: () -> Unit) {
        val beats = getAuthorDataFromFile(filePath)
            .trim()
            .lines()
            .map { line ->
                line.split(",")
                    .filter { it.isNotEmpty() }
                    .map { it.toInt() }
                    .toIntArray()
            }

        if(beats.isEmpty()) return
        numZones = beats[0].size

        exitFlag = false
        Thread {
            var mediaPlayer: MediaPlayer? = null
            val sleepTime = 16_666_000L // 16.666 milliseconds

            mGM?.openSession()
            for (beat in beats) {
                val frame = mGM!!.glyphFrameBuilder

                for ((zone, intensity) in beat.withIndex()) {
                    if (intensity != 0) {
                        if(numZones == 5) {
                            val glyphs = getGlyphMapping(zone)
                            for (glyph in glyphs) frame.buildChannel(glyph, intensity)
                        }
                        else frame.buildChannel(zone, intensity)
                    }
                }

                mGM!!.toggle(frame.build())

                if (mediaPlayer == null) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(filePath)
                        prepare()
                        start()
                    }
                }

                val startTime = System.nanoTime()

                while (System.nanoTime() - startTime < sleepTime) {
                    // busy-wait loop to achieve precise timing
                }

                if (exitFlag) {
                    mGM?.turnOff()
                    mGM?.closeSession()
                    mediaPlayer.stop()
                    mediaPlayer.release()
                    onFinished()
                    return@Thread
                }
            }

            mGM?.turnOff()
            mGM?.closeSession()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            onFinished()
        }.start()
    }

    fun stopVisualization() {
        exitFlag = true
    }

    override fun close() {
        try {
            mGM?.turnOff()
            mGM?.closeSession()
        }
        catch (e: GlyphException) {
            e.printStackTrace()
        }
        mGM?.unInit()
    }
}