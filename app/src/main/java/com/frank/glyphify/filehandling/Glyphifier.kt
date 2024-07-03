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
import com.frank.glyphify.filehandling.Constants.ALBUM_NAME
import com.frank.glyphify.filehandling.Constants.LEDS_PATTERN
import com.frank.glyphify.filehandling.Constants.LIGHT_DURATION_MS
import com.frank.glyphify.filehandling.Constants.MAX_LIGHT
import com.frank.glyphify.filehandling.Constants.NUM_ZONES
import com.frank.glyphify.filehandling.FileHandling.compressAndEncode
import com.frank.glyphify.filehandling.FileHandling.getAudioDetails
import com.frank.glyphify.filehandling.FileHandling.getFileExtension
import com.frank.glyphify.filehandling.LightEffects.circusTent
import com.frank.glyphify.filehandling.LightEffects.expDecay
import com.frank.glyphify.filehandling.LightEffects.fastBlink
import com.frank.glyphify.filehandling.LightEffects.flickering
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.util.SortedMap
import java.util.zip.Deflater
import kotlin.math.pow
import kotlin.random.Random


class Glyphifier(private val context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {

    private val path = context.filesDir.path
    private val uri = Uri.parse(inputData.getString("uri"))
    private val outName = inputData.getString("outputName")


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

    /**
     * Given a beat array it normalizes the timestamp to the closest timestamp multiple of
     * LIGHT_DURATION_MS and maps the energy to a value between 0 and MAX_LIGHT
     * @param beats: beats array
     * @return the normalized beats array
     * */
    private fun normalizeBeats(beats: List<Pair<Int, Double>>): List<Pair<Int, Int>> {
        val avgEnergy = calculateAverageEnergy(beats)
        return beats.map { (time, energy) ->
            val normalizedTime = (time + LIGHT_DURATION_MS / 2) / LIGHT_DURATION_MS * LIGHT_DURATION_MS
            val normalizedEnergy = (energy * (MAX_LIGHT / (2.0 * avgEnergy))).toInt()
            Pair(normalizedTime, normalizedEnergy)
        }
    }

    /**
     * Converts a 2D array of beats to a sorted map, grouping on the timestamp
     * @param bandsBeats: 2D array of beats
     * @return sorted map of beats grouped by timestamp
     * */
    private fun beats2Map(bandsBeats: List<List<Pair<Int, Double>>>): SortedMap<Int, List<Int>> {
        val normalizedBandsBeats = bandsBeats.map { normalizeBeats(it) }

        val bandBeatsMap = mutableMapOf<Int, MutableList<Int>>()
        for ((bandNum, beats) in normalizedBandsBeats.withIndex()) {
            for ((timestamp, lightIntensity) in beats) {
                if (timestamp !in bandBeatsMap) {
                    bandBeatsMap[timestamp] = MutableList(NUM_ZONES) { if (it == bandNum) lightIntensity else 0 }
                }
                else {
                    bandBeatsMap[timestamp]?.set(bandNum, lightIntensity)
                }
            }
        }

        return bandBeatsMap.toSortedMap()
    }

    /**
     * Randomizes which zone is used to represent high frequencies for a given beat
     * @param bandsBeatsMap: beats map grouped by timestamp
     * @return randomized beats map grouped by timestamp
     * */
    private fun randomizeLightEffectPosition(bandsBeatsMap: Map<Int, List<Int>>): MutableMap<Int, MutableList<Int>> {
        val randomizedBeats = mutableMapOf<Int, MutableList<Int>>()

        for ((timestamp, lightIntensities) in bandsBeatsMap) {
            // find max value across designed high frequencies zones
            val maxValue = lightIntensities.withIndex().filter { it.index <= 1 || it.index == 3 }.maxOf { it.value }

            val highIndices = listOf(0, 1, 3)
            val randomChoice = Random.nextInt(3)

            // reset every zone
            randomizedBeats[timestamp] = lightIntensities.toMutableList()
            randomizedBeats[timestamp]?.set(0, 0)
            randomizedBeats[timestamp]?.set(1, 0)
            randomizedBeats[timestamp]?.set(3, 0)

            // set zones randomly
            when (randomChoice) {
                0 -> { // 1/3 of the time, only one zone is used
                    val highIndex = highIndices[Random.nextInt(highIndices.size)]
                    randomizedBeats[timestamp]?.set(highIndex, maxValue)
                }
                1 -> { // 1/3 of the time, two random zones out of three are used
                    val shuffledIndices = highIndices.shuffled()
                    randomizedBeats[timestamp]?.set(shuffledIndices[0], maxValue)
                    randomizedBeats[timestamp]?.set(shuffledIndices[1], maxValue)
                }
                2 -> { // 1/3 of the time, all three zones are used
                    for (index in highIndices) {
                        randomizedBeats[timestamp]?.set(index, maxValue)
                    }
                }
            }
        }

        return randomizedBeats
    }

    /**
     * If a time slot has more than 1 zone active, those zones are redistributed randomly in time
     * @param bandsBeatsMap: beats map grouped by timestamp
     * @return time distributed beats map grouped by timestamp
     * */
    private fun distributeBeats(bandsBeatsMap: Map<Int, List<Int>>): MutableMap<Int, MutableList<Int>> {
        val distributedBeats = mutableMapOf<Int, MutableList<Int>>()

        val offset = 6 * LIGHT_DURATION_MS
        var newTs = 0

        for ((key, values) in bandsBeatsMap) {
            if (values.count { it != 0 } > 1) {

                for (i in 0 until 5) {

                    if (values[i] != 0) {
                        val rand = Random.nextInt(1, 3)
                        val randOffset = offset / rand
                        when(i) {
                            0,1 -> newTs = key - randOffset
                            2 -> newTs = key
                            3,4 -> newTs = key + randOffset
                        }

                        if (newTs !in distributedBeats) {
                            distributedBeats[newTs] = MutableList(NUM_ZONES) { if (it == i) values[i] else 0 }
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
     * Add a randomized light effect to each zone in each time slot
     * @param bandsBeatsMap: beats map grouped by timestamp
     * @return beats map grouped by timestamp with random effects
     * */
    private fun addBeatsEffects(bandsBeatsList: MutableMap<Int, MutableList<Int>>):
            MutableMap<Int, MutableList<Int>> {

        val toZones = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()

        // first group beats into zones
        for ((key, values) in bandsBeatsList) {
            for ((index, value) in values.withIndex()) {
                if(value != 0) {
                    if (index !in toZones) {
                        toZones[index] = MutableList(1) { Pair<Int, Int>(key, value) }
                    }
                    else {
                        toZones[index]?.add(Pair<Int, Int>(key, value))
                    }
                }
            }
        }

        val fadedBeats = mutableMapOf<Int, MutableList<Int>>()

        // then apply effects for each pair of (timestamp, light)
        for ((zone, beats) in toZones) {
            val tmp: MutableList<Pair<Int, Int>> = mutableListOf()

            for (beat in beats) {
                // these values have been fine tuned across multiple tries
                when(zone) {
                    0 -> {
                        if(Random.nextInt(0, 2) == 1) {
                            tmp.addAll(expDecay(beat, 2, 18))
                        }
                        tmp.addAll(circusTent(beat, 6))
                    }
                    1 -> {
                        if(Random.nextInt(0, 2) == 1) {
                            tmp.addAll(expDecay(beat, 2, 15))
                        }
                        tmp.addAll(circusTent(beat, 8))
                    }
                    2 -> {
                        if(Random.nextInt(0, 2) == 1) {
                            tmp.addAll(expDecay(beat, 2, 22))
                        }
                        else tmp.addAll(circusTent(beat, 10))
                    }
                    3 -> {
                        if(Random.nextInt(0, 2) == 1) {
                            tmp.addAll(expDecay(beat, 2, 15))
                        }
                        else tmp.addAll(circusTent(beat, 10))
                    }
                    4 -> {
                        if(Random.nextInt(0, 2) == 1) {
                            tmp.addAll(flickering(beat, 3, 3))
                        }
                        else tmp.addAll(fastBlink(beat, 5))
                    }
                }
            }

            for ((timestamp, lightIntensity) in tmp) {
                fadedBeats.getOrPut(timestamp) { MutableList(NUM_ZONES) { 0 } }.apply {
                    // if some light intensity data is overlapping chose the bigger one
                    this[zone] = if(lightIntensity > this[zone]) lightIntensity else this[zone]
                    this[zone] = if(this[zone] > MAX_LIGHT) MAX_LIGHT else this[zone]
                }
            }
        }

        return fadedBeats.toSortedMap()
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
    private fun buildAuthorTag(data: MutableMap<Int, MutableList<Int>>): String {
        val keys = data.keys.toList()

        val result = mutableListOf<MutableList<Int>>()

        var currentTs = 0

        for (i in keys.indices) {
            val nextTs = keys[i]

            val numEmpty = (nextTs - currentTs) / 16 - 1
            if (numEmpty > 0) {
                result.addAll(List(numEmpty) { MutableList(NUM_ZONES) { 0 } })
            }

            var currentData = data[nextTs]!!
            result.add(currentData)

            currentTs = nextTs
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
        val appVers = sharedPref.getString("appVersion", "v1-Spacewar Glyph Composer")

        try {
            // convert from wav to ogg
            val session = FFmpegKit.execute("-i $path/temp.wav -c:a libopus " +
                    "-metadata COMPOSER='$appVers' " +
                    "-metadata TITLE='$ALBUM_NAME' " +
                    "-metadata ALBUM='$ALBUM_NAME' " +
                    "-metadata CUSTOM1='$custom1Tag' " +
                    "-metadata CUSTOM2='$NUM_ZONES'cols " +
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
                put(MediaStore.Audio.AudioColumns.COMPOSER, appVers)
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

        val randomizedBeats = randomizeLightEffectPosition(normalizedBeats)
        setProgressAsync(workDataOf("PROGRESS" to 50))

        val distributedBeats = distributeBeats(randomizedBeats)
        setProgressAsync(workDataOf("PROGRESS" to 60))

        val fadedBeats = addBeatsEffects(distributedBeats)
        setProgressAsync(workDataOf("PROGRESS" to 70))

        val authorTag = buildAuthorTag(fadedBeats)
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