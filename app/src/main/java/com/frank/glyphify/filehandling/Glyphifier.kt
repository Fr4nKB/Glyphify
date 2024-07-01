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
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.util.SortedMap
import java.util.zip.Deflater
import kotlin.random.Random


class Glyphifier(private val context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {

    companion object {
        const val MAX_LIGHT = 4096
        const val LIGHT_DURATION_MS = 16
        const val LEDS_PATTERN = "-0,-1,-2,-3,-4,c-0,-4,c-0,-3,-4,s-0,-1,-2,-3,-4,c-4,c-4,s-0," +
                "-1,-2,-4,c-2,-4,c-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,c-0,-2,c-0,-1,-2,s-0,-1,-2,-3," +
                "-4,c-2,c-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,c-0,-2,c-0,s-0,-1,-2,-4," +
                "c-2,-4,c-0,-1,-2,-3,-4"
        const val ALBUM_NAME = "Glyphify"
    }
    private val path = context.filesDir.path
    private val uri = Uri.parse(inputData.getString("uri"))
    private val outName = inputData.getString("outputName")

    private fun getFileExtension(uri: Uri, contentResolver: ContentResolver): String {
        var extension: String? = null

        // check uri format to avoid null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            // the file is stored in the provider with a ContentProvider
            val mime = MimeTypeMap.getSingleton()
            extension = mime.getExtensionFromMimeType(contentResolver.getType(uri))
        }
        else {
            extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(uri.path)).toString())
            extension = extension?.lowercase(Locale.ROOT)
        }

        return extension ?: ""
    }

    /**
     * Retrieves details of a wav audio file using FFprobe
     * @param filePath: path of the wav file
     * @return duration and sample rate
     * @throws RuntimeException if something went wrong
     * */
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

    /**
     * Compress data using zlib and then encodes it in base64
     * @param data: the data to work on
     * @return a string containing the base64 representation of the compresse data
     * */
    fun compressAndEncode(data: String): String {
        val input = data.toByteArray(Charsets.UTF_8)

        // Compress the bytes
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
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
        var base64Data = Base64.encodeToString(compressedBytes, Base64.DEFAULT)

        // Remove padding bytes
        base64Data = base64Data.trimEnd('=')

        // Add newline every 76 characters
        val formattedData = base64Data.chunked(76).joinToString("\n")

        return "$formattedData\n"
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

        // create a local file from the uri so that ffmpeg can access it
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

        // if the audio is not wav convert it using ffmpeg
        if(ext != "wav") {
            val session = FFmpegKit.execute("-i $path/temp.$ext $path/temp.wav -y")
            if(!ReturnCode.isSuccess(session.returnCode)){
                throw RuntimeException("Failed conversion")
            }
        }
        return true
    }

    private fun calculateAverageEnergy(beatsBand: List<Pair<Int, Double>>): Double {
        val totalEnergy = beatsBand.sumOf { it.second }
        val averageEnergy = if (beatsBand.isNotEmpty()) totalEnergy / beatsBand.size else 0.0
        return averageEnergy
    }

    private fun normalizeBeats(beats: List<Pair<Int, Double>>): List<Pair<Double, Int>> {
        val avgEnergy = calculateAverageEnergy(beats)
        return beats.map { (time, energy) ->
            val normalizedTime = kotlin.math.round((time / LIGHT_DURATION_MS).toDouble()) * LIGHT_DURATION_MS
            val normalizedEnergy = (energy * (MAX_LIGHT / (2.0 * avgEnergy))).toInt()
            Pair(normalizedTime, normalizedEnergy)
        }
    }

    private fun beats2Map(bandsBeats: List<List<Pair<Int, Double>>>): SortedMap<Double, List<Int>> {
        val normalizedBandsBeats = bandsBeats.map { normalizeBeats(it) }

        val bandBeatsMap = mutableMapOf<Double, MutableList<Int>>()
        for ((bandNum, beats) in normalizedBandsBeats.withIndex()) {
            for ((timestamp, lightIntensity) in beats) {
                if (timestamp !in bandBeatsMap) {
                    bandBeatsMap[timestamp] = MutableList(5) { if (it == bandNum) lightIntensity else 0 }
                }
                else {
                    bandBeatsMap[timestamp]?.set(bandNum, lightIntensity)
                }
            }
        }

        return bandBeatsMap.toSortedMap()
    }

    fun distributeBeats(bandsBeatsMap: Map<Double, List<Int>>): MutableMap<Double, MutableList<Int>> {
        val distributedBeats = mutableMapOf<Double, MutableList<Int>>()
        for ((key, values) in bandsBeatsMap) {
            if (values.count { it != 0 } > 1) {
                for (i in 0 until 5) {
                    if (values[i] != 0) {
                        var offset = 0
                        if (Random.nextInt(0, 2) == 0) {
                            offset -= Random.nextInt(1, 3) * LIGHT_DURATION_MS
                        }
                        else {
                            offset += Random.nextInt(1, 3) * LIGHT_DURATION_MS
                        }

                        val newTs = key + offset

                        if (newTs !in distributedBeats) {
                            distributedBeats[newTs] = MutableList(5) { if (it == i) values[i] else 0 }
                        }
                        else {
                            distributedBeats[newTs]?.set(i, values[i])
                        }
                    }
                }
            }
            else {
                distributedBeats[key] = values.toMutableList()
            }
        }

        return distributedBeats
    }

    /**
     * Build a Custom1 tag which shows the string 'GLIPHIFY' in the Composer app
     * @param audioLen: the duration of the audio in seconds
     * @return the compressed and encoded data for the preview
     * */
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

    /**
     * Build the Author tag which is responsible for the Glyph show
     * @param sampleRate: sample rate of the audio to be set as ringtone
     * @return the compressed and encoded data for the Glyph show
     * */
    private fun buildAuthorTag(data: MutableMap<Double, MutableList<Int>>, linDecay: Int): String {
        val keys = data.keys.toList()

        val result = mutableListOf<MutableList<Int>>()

        var currentTs = keys[0]
        var numEmpty = (currentTs / 16).toInt() - 1
        if (numEmpty > 0) {
            result.addAll(List(numEmpty) { mutableListOf(0, 0, 0, 0, 0) })
        }

        for (i in keys.indices) {
            val nextTs = if (i + 1 < keys.size) keys[i + 1] else 1.0

            var currentData = data[keys[i]]!!
            result.add(currentData)

            while (currentTs < nextTs) {
                currentTs += LIGHT_DURATION_MS
                val nextItem = mutableListOf<Int>()
                var allZero = 0

                for (elem in currentData) {
                    if (elem != 0) {
                        val newVal = if (elem - linDecay < 0) 0 else elem - linDecay
                        nextItem.add(newVal)
                    }
                    else {
                        allZero++
                        nextItem.add(0)
                    }
                }

                result.add(nextItem)
                if (allZero == 5) {
                    break
                }

                currentData = nextItem
            }

            numEmpty = ((nextTs - currentTs) / 16).toInt() - 1
            if (numEmpty > 0) {
                result.addAll(List(numEmpty) { mutableListOf(0, 0, 0, 0, 0) })
            }
            currentTs += numEmpty * LIGHT_DURATION_MS
        }

        val lines = result.joinToString(",\r\n") { it.joinToString(",") }

        // Join all lines into a single string, with each line ending with ',\r\n'
        val csvString = "$lines,\r\n"

        return compressAndEncode(csvString)
    }

    /**
     * Given a name it checks if a file already exists with that name and if it does it modifies the
     * name to be unique by adding a timestamp at the end of it
     * @param originalName: name to check for duplicate
     * @return the new unique name
     * */
    private fun getUniqueName(originalName: String): String {
        var uniqueName = originalName

        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("$originalName.ogg")

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)
        if (cursor?.moveToFirst() == true) {
            // File with the same name exists, append a unique identifier to the output name
            uniqueName = "$originalName-${System.currentTimeMillis()}"
        }
        cursor?.close()

        return uniqueName
    }

    /**
     * Creates the final ringtone with all the data necessary for the Glyph show
     * @param outputName: name of the ringtone
     * @param custom1Tag: compressed and encoded data for the Glyph preview
     * @param authorTag: compressed and encoded data for the Glyph show
     * @return true if successful, false otherwise
     * */
    private fun buildOgg(outputName: String, custom1Tag: String, authorTag: String): Boolean {
        val sharedPref: SharedPreferences =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val appVers = sharedPref.getString("appVersion", "Spacewar Glyph Composer")

        try {
            // convert from wav to ogg
            val session = FFmpegKit.execute("-i $path/temp.wav -c:a libopus " +
                    "-metadata COMPOSER='$appVers' " +
                    "-metadata TITLE='$ALBUM_NAME' " +
                    "-metadata ALBUM='$ALBUM_NAME' " +
                    "-metadata CUSTOM1='$custom1Tag' " +
                    "-metadata AUTHOR='$authorTag' " +
                    "\"$path/$outputName.ogg\" -y")

            if(!ReturnCode.isSuccess(session.returnCode)) return false

            // export ringtone to media store so that android OS can access it
            // if name already in use the timestamp is appended at the end
            val uniqueName = getUniqueName(outputName)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$uniqueName.ogg")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + File.separator + "Compositions")
                put(MediaStore.Audio.AudioColumns.COMPOSER, "Spacewar Glyph Composer")
                put(MediaStore.Audio.AudioColumns.ALBUM, ALBUM_NAME)
                put(MediaStore.Audio.AudioColumns.TITLE, ALBUM_NAME)
                put(MediaStore.Audio.AudioColumns.IS_RINGTONE, true)
                put(MediaStore.Audio.AudioColumns.ARTIST, authorTag)
            }

            val uri: Uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw RuntimeException("Failed to build ogg")

            // save uri in the shared preferences so it can be later access to set the ringtone
            val sharedPref: SharedPreferences = context.getSharedPreferences("URIS", Context.MODE_PRIVATE)
            val editor: SharedPreferences.Editor = sharedPref.edit()
            editor.putString(uniqueName, uri.toString())
            editor.apply()

            // copy data from local file to the exported one
            val oggFile = File("$path/$outputName.ogg")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                oggFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // the ringtone has been exported, remove it from app's filesystem
            oggFile.delete()
            return true
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
        val rawBeats = BeatDetector.detectBeatsAndFrequencies(context, "$path/temp.wav")
        setProgressAsync(workDataOf("PROGRESS" to 30))
        val normalizedBeats = beats2Map(rawBeats)
        setProgressAsync(workDataOf("PROGRESS" to 40))
        val distributedBeats = distributeBeats(normalizedBeats)
        setProgressAsync(workDataOf("PROGRESS" to 50))
        val authorTag = buildAuthorTag(distributedBeats, 100)
        if (authorTag == "") return Result.failure()

        setProgressAsync(workDataOf("PROGRESS" to 80))

        // create ogg file which contains the Glyphified song
        if(outName == null) return Result.failure()
        if(!buildOgg(outName, custom1Tag, authorTag)) return Result.failure()

        setProgressAsync(workDataOf("PROGRESS" to 100))
        Thread.sleep(100)   // needed to show the progress bar at 100%

        return Result.success()
    }
}