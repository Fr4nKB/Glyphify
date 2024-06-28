package com.frank.glyphify.filehandling

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import org.jtransforms.fft.FloatFFT_1D
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.Deflater
import kotlin.math.hypot


class Glyphifier(private val context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {

    companion object {
        const val MAX_LIGHT = 4096
        const val LEDS_PATTERN = "-0,-1,-2,-3,-4,c-0,-4,c-0,-3,-4,s-0,-1,-2,-3,-4,c-4,c-4,s-0," +
                "-1,-2,-4,c-2,-4,c-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,c-0,-2,c-0,-1,-2,s-0,-1,-2,-3," +
                "-4,c-2,c-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,c-0,-2,c-0,s-0,-1,-2,-4," +
                "c-2,-4,c-0,-1,-2,-3,-4"
    }
    private val path = context.filesDir.path
    private val uri = Uri.parse(inputData.getString("uri"))

    fun getFileExtension(uri: Uri, contentResolver: ContentResolver): String {
        var extension: String? = null

        // Check uri format to avoid null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            // The file is stored in the provider with a ContentProvider
            val mime = MimeTypeMap.getSingleton()
            extension = mime.getExtensionFromMimeType(contentResolver.getType(uri))
        }
        else {
            extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(uri.path)).toString())
            extension = extension?.lowercase(Locale.ROOT)
        }

        return extension ?: ""
    }

    private fun getAudioDetails(filePath: String): Pair<Double, Int> {
        try {
            val mediaInformation = FFprobeKit.getMediaInformation(filePath).mediaInformation
            val duration = mediaInformation.duration.toDouble()
            val streams = mediaInformation.streams
            for (stream in streams) {
                if (stream.type == "audio") {
                    val sampleRate = stream.sampleRate.toInt()
                    return Pair(duration, sampleRate)
                }
            }
            return Pair(-1.0, -1)
        } catch (e: RuntimeException) {
            throw e
        }
    }

    fun compressAndEncode(data: String): String {
        val input = data.toByteArray(Charsets.UTF_8)

        // Compress the bytes
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer) // compress data
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        val compressedBytes = outputStream.toByteArray()

        // Encode to Base64
        val base64Data = Base64.encodeToString(compressedBytes, Base64.DEFAULT)

        return base64Data
    }

    /**
     * Loads the file specified in the uri in the app's filesystem as a temporary file,
     * eventually converts mp3 to wav
     * @param uri: the uri of the file to be loaded
     * @return true if file successfully loaded
     * @throws RuntimeException if something went wrong
     * */
    private fun prepareInput(uri: Uri): Boolean {
        // get file extension
        val ext = getFileExtension(uri, context.contentResolver)
        if (ext == "") throw RuntimeException("No such file")
        else if (ext != "mp3" && ext != "wav") throw RuntimeException("Unsupported extension")

        // load the file from uri
        val tempFile = File(path, "temp.$ext")
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(tempFile)

            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
        }
        catch (e: Exception) {
            throw RuntimeException("Failed to load file")
        }

        //conversion from mp3 to wav using ffmpeg
        if(ext == "mp3") {
            val session = FFmpegKit.execute("-i $path/temp.mp3 $path/temp.wav -y")
            if(!ReturnCode.isSuccess(session.returnCode)){
                Log.d("DEBUG", String.format("Command failed with state %s and rc %s.%s",
                    session.getState(), session.getReturnCode(), session.getFailStackTrace()));
                throw RuntimeException("Failed conversion")
            }
        }
        return true
    }

    private fun getMonoAudioData(): ArrayList<Float> {
        // prepare to read wav file
        val wavFile = File("$path/temp.wav")
        val headerSize = 44     // common header size
        val audioDataBytes = ByteArray(wavFile.length().toInt() - headerSize)

        // load and skip header
        val inputStream = FileInputStream(wavFile)
        inputStream.skip(headerSize.toLong())

        // read the audio data, convert from to stereo to mono by doing the average of channels
        inputStream.read(audioDataBytes)
        inputStream.close()

        val audioData = ByteBuffer.wrap(audioDataBytes).order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val monoData = ArrayList<Float>(audioData.remaining() / 2)
        while (audioData.remaining() >= 2) {
            val left = audioData.get()
            val right = audioData.get()
            monoData.add((left + right) / 2.0f)
        }

        return monoData
    }

    private fun deriveLEDData(results:  Array<FloatArray>): MutableList<Array<Int>> {

        // normalize the results to be between 0 and 4095
        // calculate the average amplitude for each region
        val avgValuePerColumn = results[0].indices.map { i -> results.map { it[i] }.average() }
        // normalize based on the average value
        for (i in results.indices) {
            for (j in results[i].indices) {
                results[i][j] *= (MAX_LIGHT / (2 * avgValuePerColumn[j])).toFloat()

                // clamping to not exceed the maximum value for light intensity
                if (results[i][j] > MAX_LIGHT) {
                    results[i][j] = (MAX_LIGHT - 1).toFloat()
                }
            }
        }

        // convert to integers
        val resultsInt: Array<Array<Int>> = results.map { row ->
            row.map { it.toInt() }.toTypedArray()
        }.toTypedArray()

        // spread light pattern in the 48ms window
        // this makes the light effect more evident
        val lightData = mutableListOf<Array<Int>>()
        for (row in resultsInt) {
            // each line lasts 16ms, thus 3 lines are needed
            for (i in 0 until 3) {
                val newRow = when(i) {
                    0 -> arrayOf(row[0], row[1], 0, 0, 0)
                    1 -> arrayOf(0, 0, row[2], 0, 0)
                    else -> arrayOf(0, 0, 0, row[3], row[4])
                }
                lightData.add(newRow)
            }
        }

        return lightData
    }

    private fun buildCustom1Tag(audioLen: Double): String {
        val ledsPattern = LEDS_PATTERN.split(",")

        // 10s -> 400ms for close led, 600ms for a space
        // 1s -> 40ms, 60ms
        val closeLed = (audioLen * 40).toInt()
        val space = (audioLen * 60).toInt()

        var timestamp = 0
        var text = ""
        for ((count, pattern) in ledsPattern.withIndex()) {
            val (action, led) = pattern.split("-")
            when (action) {
                "" -> text += "$timestamp-$led"
                "c" -> {
                    timestamp += closeLed
                    text += "$timestamp-$led"
                }
                "s" -> {
                    timestamp += space
                    text += "$timestamp-$led"
                }
            }
            if (count != ledsPattern.size - 1) {
                text += ","
            }
        }

        return compressAndEncode(text)
    }

    private fun buildAuthorTag(sampleRate: Int): String {

        val monoData = getMonoAudioData()

        // define frequency regions according to sample rate
        val freqRegions = floatArrayOf(0f, sampleRate * 0.02f, sampleRate * 0.04f,
            sampleRate * 0.06f, sampleRate * 0.08f, sampleRate * 0.1f)

        // calculate window size for 48ms
        val windowSize = (sampleRate * 0.048).toInt()

        // create hanning window
        val hanningWindow = FloatArray(windowSize) {
            (0.5f - 0.5f * kotlin.math.cos(2.0 * kotlin.math.PI * it / windowSize)).toFloat()
        }

        // convert frequency regions to indices
        val freqRegionsIndices = IntArray(freqRegions.size) {
            (freqRegions[it] / (sampleRate / 2) * (windowSize / 2)).toInt()
        }

        val resultsSpectrum = mutableListOf<FloatArray>()   // hold results

        // Iterate over audio data with step size equal to window size
        // Only process slices of data that are equal to the window size
        val windows = monoData.windowed(windowSize, windowSize, false)
        for (readOnlyWindow in windows) {
            val window = readOnlyWindow.toMutableList()
            // apply hanning window
            for (j in window.indices) {
                window[j] *= hanningWindow[j]
            }

            // apply FFT
            val fft = FloatFFT_1D(windowSize.toLong())
            fft.realForward(window.toFloatArray())

            // calculate average amplitude for each region
            val avgAmplitudes = FloatArray(freqRegionsIndices.size - 1)
            for (j in avgAmplitudes.indices) {
                val start = freqRegionsIndices[j]
                val end = freqRegionsIndices[j + 1]
                avgAmplitudes[j] = window.subList(start, end).map { hypot(it, 0.0f) }.average()
                    .toFloat()
            }

            resultsSpectrum.add(avgAmplitudes)
        }

        val results = resultsSpectrum.toTypedArray()

        // derive LED data from average amplitudes in each region
        val lightData = deriveLEDData(results)

        // convert to csv
        val csvBuilder = StringBuilder()
        for (row in lightData) {
            csvBuilder.append(row.joinToString(","))
            csvBuilder.append("\n")
        }
        val csvString = csvBuilder.toString()

        return compressAndEncode(csvString)
    }

    private fun buildOgg(outputName: String, custom1Tag: String, authorTag: String) {
        try {
            val session = FFmpegKit.execute("-i $path/temp.wav -c:a libopus " +
                    "-metadata COMPOSER='Spacewar Glyph Composer' " +
                    "-metadata TITLE='Glyphify' " +
                    "-metadata ALBUM='Glyphify' " +
                    "-metadata CUSTOM1='$custom1Tag' " +
                    "-metadata AUTHOR='$authorTag' " +
                    "$path/$outputName.ogg -y")

            if(ReturnCode.isSuccess(session.returnCode)) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$outputName.ogg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + File.separator + "Compositions")
                    put(MediaStore.Audio.AudioColumns.COMPOSER, "Spacewar Glyph Composer")
                    put(MediaStore.Audio.AudioColumns.ALBUM, "Glyphify")
                    put(MediaStore.Audio.AudioColumns.TITLE, "Glyphify")
                    put(MediaStore.Audio.AudioColumns.IS_RINGTONE, true)
                    put(MediaStore.Audio.AudioColumns.ARTIST, authorTag)
                    // Add more metadata as needed
                }

                val uri: Uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw RuntimeException("Failed to build ogg")

                val sharedPref: SharedPreferences = context.getSharedPreferences("URIS", Context.MODE_PRIVATE)
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putString("$outputName", uri.toString())
                editor.apply()

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    File("$path/$outputName.ogg").inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
        catch (e: Exception) {
            throw (e)
        }
    }

    override fun doWork(): Result {
        // copy input file into app's filesystem, convert to wav if necessary
        try {
            prepareInput(uri)
        }
        catch (e: Exception) {
            Log.d("DEBUG", e.toString())
            return Result.failure()
        }

        setProgressAsync(workDataOf("PROGRESS" to 10))

        // get audio duration and sample rate
        val audioInfo = getAudioDetails("$path/temp.wav")
        if (audioInfo.first == -1.0 || audioInfo.second == 1) return Result.failure()

        setProgressAsync(workDataOf("PROGRESS" to 15))

        // build custom1 GLYPHIFY text for composer app
        val custom1Tag = buildCustom1Tag(audioInfo.first)

        setProgressAsync(workDataOf("PROGRESS" to 20))

        // build led animation based on selected song
        val authorTag = buildAuthorTag(audioInfo.second)
        if (authorTag == "") return Result.failure()

        setProgressAsync(workDataOf("PROGRESS" to 80))

        // create ogg file which contains the Glyphified song
        val outName = inputData.getString("outputName") ?: return Result.failure()
        buildOgg(outName, custom1Tag, authorTag)

        setProgressAsync(workDataOf("PROGRESS" to 100))
        Thread.sleep(100)   // needed to show the progress bar at 100%

        return Result.success()
    }
}