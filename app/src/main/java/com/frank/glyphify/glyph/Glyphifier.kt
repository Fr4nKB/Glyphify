package com.frank.glyphify.glyph

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Level
import com.frank.glyphify.Constants.ALBUM_NAME
import com.frank.glyphify.Constants.LEDS_PATTERN
import com.frank.glyphify.Constants.LIGHT_DURATION_MS
import com.frank.glyphify.Constants.MAX_LIGHT
import com.frank.glyphify.Constants.PHONE1_MODEL_ID
import com.frank.glyphify.Constants.PHONE2_MODEL_ID
import com.frank.glyphify.glyph.FileHandling.compressAndEncode
import com.frank.glyphify.glyph.FileHandling.getAudioDetails
import com.frank.glyphify.glyph.FileHandling.getFileExtension
import com.frank.glyphify.glyph.LightEffects.circusTent
import com.frank.glyphify.glyph.LightEffects.expDecay
import com.frank.glyphify.glyph.LightEffects.fastBlink
import com.frank.glyphify.glyph.LightEffects.flickering
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.SortedMap
import kotlin.random.Random


class Glyphifier(private val context: Context, workerParams: WorkerParameters):
    Worker(context, workerParams) {

    private val path = context.filesDir.path
    private val uri = Uri.parse(inputData.getString("uri"))
    private val expanded = inputData.getBoolean("expanded", false)
    private var numZones = 5
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
        val tmpName = if(ext == "wav") "tempWav.$ext" else "temp.$ext"
        val tempFile = File(path, tmpName)
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
        val session = FFmpegKit.execute("-i $path/$tmpName -ac 2 -ar 44100 $path/temp.wav -y")
        if(!ReturnCode.isSuccess(session.returnCode)){
            throw RuntimeException("Failed conversion")
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

        Log.d("DEBUG", normalizedBandsBeats[3].toString())

        val bandBeatsMap = mutableMapOf<Int, MutableList<Int>>()
        for ((bandNum, beats) in normalizedBandsBeats.withIndex()) {
            for ((timestamp, lightIntensity) in beats) {
                if (timestamp !in bandBeatsMap) {
                    bandBeatsMap[timestamp] = MutableList(numZones) { if (it == bandNum) lightIntensity else 0 }
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
        val highBeatIndices = listOf(0, 1, 3)
        val lowBeatIndices = listOf(2, 4)

        for ((timestamp, lightIntensities) in bandsBeatsMap) {
            //randomize high and low beats
            val shuffledList = lightIntensities.toMutableList()
            shuffledList.shuffle(Random)

            // encode to what frequency band (high or low) this beat belongs
            val isLowBeat = !(highBeatIndices.all { lightIntensities[it] == 0 }
                    && lowBeatIndices.any { lightIntensities[it] > 0 })
            val isHighBeat = lowBeatIndices.all { lightIntensities[it] == 0 }
                    && highBeatIndices.any { lightIntensities[it] > 0 }

            // if both frequencies are present, categorize as high beat
            val beatType = if (isLowBeat || isHighBeat) 1 else 0
            shuffledList.add(shuffledList.size, beatType)

            randomizedBeats[timestamp] = shuffledList
        }

        return randomizedBeats
    }

    /**
     * Expand the beats to work on the 33 zones of Phone(2)
     * @param beatsToExpand: beats map grouped by timestamp
     * @return expanded beats
     * */
    private fun expandZones(beatsToExpand: Map<Int, List<Int>>, newNumZones: Int):
            MutableMap<Int, MutableList<Int>> {
        val expandedZones = mutableMapOf<Int, MutableList<Int>>()

        if(newNumZones == 11) {
            for ((timestamp, lightIntensities) in beatsToExpand) {

                var expandedList = MutableList(newNumZones + 1) { 0 }

                for ((index, lightIntesity) in lightIntensities.withIndex()) {
                    when(index){
                        0 -> {  // expand zone 0 to use one or both subzones
                            val choice = Random.nextInt(0, 3)
                            if (choice == 2) {
                                expandedList[0] = lightIntesity
                                expandedList[1] = lightIntesity
                            }
                            else {
                                expandedList[choice] = lightIntesity
                            }
                        }
                        1 -> {
                            expandedList[2] = lightIntesity
                        }
                        2 -> {
                            // zone 2 can use only one subzone, two diagonal subzones, triangular subzones
                            // or as a whole
                            val choice = Random.nextInt(0, 4)

                            when(choice) {
                                0 -> {
                                    val subZoneIndex = Random.nextInt(0, 6)
                                    expandedList[subZoneIndex + 3] = lightIntesity
                                }
                                1 -> {
                                    val subZoneIndex = Random.nextInt(0, 3)
                                    expandedList[subZoneIndex + 3] = lightIntesity
                                    expandedList[subZoneIndex + 6] = lightIntesity
                                }
                                2 -> {
                                    val subZoneIndex = Random.nextInt(0, 2)
                                    expandedList[subZoneIndex + 3] = lightIntesity
                                    expandedList[subZoneIndex + 5] = lightIntesity
                                    expandedList[subZoneIndex + 7] = lightIntesity
                                }
                                3 -> {
                                    for(j in 3..6) {
                                        expandedList[j] = lightIntesity
                                    }
                                }
                            }
                        }
                        else -> {
                            expandedList[index + 6] = lightIntesity
                        }
                    }
                    expandedZones[timestamp] = expandedList
                }
            }
        }

        else if (newNumZones == 33) {
            for ((timestamp, lightIntensities) in beatsToExpand) {
                var expandedList = lightIntensities.toMutableList()

                // fetch elements to expand
                val element3 = lightIntensities[3]
                val element9 = lightIntensities[9]

                // insert 15 copies of element 3 between elements 3 and 4
                for (i in 0 until 15) {
                    expandedList.add(4, element3)
                }

                // adjust the index for element 9 after the previous insertions
                val newElement9Index = 9 + 15

                // insert 7 copies of element 9 between elements 9 and 10
                for (i in 0 until 7) {
                    expandedList.add(newElement9Index + 1, element9)
                }

                expandedZones[timestamp] = expandedList
            }

        }

        return expandedZones
    }

    /**
     * Add a randomized light effect to each zone in each time slot
     * @param bandsBeatsMap: beats map grouped by timestamp
     * @return beats map grouped by timestamp with random effects
     * */
    private fun addBeatsEffects(bandsBeatsList: MutableMap<Int, MutableList<Int>>, tempos: List<Double>):
            MutableMap<Int, MutableList<Int>> {

        val toZones = mutableMapOf<Int, MutableList<Triple<Int, Int, Int>>>()

        // first group beats into zones
        for ((key, values) in bandsBeatsList) {
            for ((index, value) in values.withIndex()) {
                if(value != 0 && index < numZones) {
                    if (index !in toZones) {
                        toZones[index] = MutableList(1) { Triple<Int, Int, Int>(key, value, values[numZones]) }
                    }
                    else {
                        toZones[index]?.add(Triple<Int, Int, Int>(key, value, values[numZones]))
                    }
                }
            }
        }

        val fadedBeats = mutableMapOf<Int, MutableList<Int>>()

        // then apply effects for each pair of (timestamp, light)
        for ((zone, beats) in toZones) {
            val tmp: MutableList<Pair<Int, Int>> = mutableListOf()

            for (beat in beats) {
                var beatWithEffect: List<Pair<Int, Int>> = emptyList()

                val effectChoice = Random.nextBoolean()

                // these values have been fine tuned across multiple tries
                when(zone) {
                    0 -> {
                        beatWithEffect = if(effectChoice) expDecay(beat, tempos)
                        else circusTent(beat, tempos)
                    }
                    1 -> {
                        beatWithEffect = if(effectChoice) expDecay(beat, tempos)
                        else circusTent(beat, tempos)
                    }
                    2 -> {
                        if(numZones == 5) {
                            beatWithEffect = if(effectChoice) expDecay(beat, tempos, 4)
                            else circusTent(beat, tempos, 4)
                        }
                        else if(numZones == 11) {
                            beatWithEffect = if(effectChoice) expDecay(beat, tempos)
                            else circusTent(beat, tempos)
                        }
                    }
                    3 -> {
                        if(numZones == 5) {
                            beatWithEffect = if(effectChoice) expDecay(beat, tempos)
                            else circusTent(beat, tempos)
                        }
                        else if(numZones == 11) {
                            beatWithEffect = if(effectChoice) expDecay(beat, tempos, 4)
                            else circusTent(beat, tempos, 4)
                        }
                    }
                    4 -> {
                        if(numZones == 5) {
                            beatWithEffect = if(effectChoice) flickering(beat, tempos, 3)
                            else fastBlink(beat, tempos)
                        }
                        else if(numZones == 11) {
                            beatWithEffect = if(effectChoice) expDecay(beat, tempos, 4)
                            else circusTent(beat, tempos, 4)
                        }
                    }
                    in 5..8 -> {
                        beatWithEffect = if(effectChoice) expDecay(beat, tempos, 4)
                        else circusTent(beat, tempos, 4)
                    }
                    9 -> {
                        beatWithEffect = if(effectChoice) expDecay(beat, tempos)
                        else circusTent(beat, tempos)
                    }
                    10 -> {
                        beatWithEffect = if(effectChoice) flickering(beat, tempos, 3)
                        else fastBlink(beat, tempos)
                    }
                }
                tmp.addAll(beatWithEffect)
            }

            for ((timestamp, lightIntensity) in tmp) {
                fadedBeats.getOrPut(timestamp) { MutableList(numZones) { 0 } }.apply {
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
                result.addAll(List(numEmpty) { MutableList(numZones) { 0 } })
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
                    "-metadata CUSTOM2='$numZones'cols " +
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
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_QUIET)
        numZones = 5

        // copy input file into app's filesystem and convert to wav
        try {
            prepareInput(uri)
        }
        catch (e: Exception) {
            return Result.failure()
        }

        setProgressAsync(workDataOf("PROGRESS" to 5))

        // get audio duration and sample rate
        val audioInfo = getAudioDetails("$path/temp.wav")
        if (audioInfo.first == -1.0 || audioInfo.second == 1) return Result.failure()

        // build custom1 GLYPHIFY text for composer app
        val custom1Tag = buildCustom1Tag(audioInfo.first)

        setProgressAsync(workDataOf("PROGRESS" to 15))

        // build led animation based on selected song
        val rawBeats = BeatDetector.detectBeatsAndFrequencies(context, path, "temp.wav")
        setProgressAsync(workDataOf("PROGRESS" to 30))

        val normalizedBeats = beats2Map(rawBeats.second)
        setProgressAsync(workDataOf("PROGRESS" to 40))

        var randomizedBeats = randomizeLightEffectPosition(normalizedBeats)
        setProgressAsync(workDataOf("PROGRESS" to 50))


        if(expanded) {        // if device is Phone(2) first expand to 11 zones
            numZones = 11
            randomizedBeats = expandZones(randomizedBeats, 11)
            setProgressAsync(workDataOf("PROGRESS" to 60))
        }

        var fadedBeats = addBeatsEffects(randomizedBeats, rawBeats.first)
        setProgressAsync(workDataOf("PROGRESS" to 70))

        if(expanded) {      // finally, if device is Phone(2), expand to 33 zones
            numZones = 33
            fadedBeats = expandZones(fadedBeats, 33)
            setProgressAsync(workDataOf("PROGRESS" to 75))
        }

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