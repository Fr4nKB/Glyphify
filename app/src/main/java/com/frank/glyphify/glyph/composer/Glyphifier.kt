package com.frank.glyphify.glyph.composer

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
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
import com.frank.glyphify.Constants.GLYPHIFY_COMPOSER_PATTERN
import com.frank.glyphify.Constants.LIGHT_DURATION_US
import com.frank.glyphify.Constants.GLYPH_MAX_INTENSITY
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID
import com.frank.glyphify.R
import com.frank.glyphify.glyph.composer.FileHandling.compressAndEncode
import com.frank.glyphify.glyph.composer.FileHandling.getAudioDetails
import com.frank.glyphify.glyph.composer.FileHandling.getFileExtension
import com.frank.glyphify.glyph.composer.LightEffects.circusTent
import com.frank.glyphify.glyph.composer.LightEffects.expDecay
import com.frank.glyphify.glyph.composer.PatternFinder.clusterizeData
import com.frank.glyphify.glyph.composer.PatternFinder.findBestCombinationOfPatterns
import com.frank.glyphify.glyph.composer.PatternFinder.findPatterns
import com.frank.glyphify.glyph.composer.PatternFinder.light_anim_high_freq
import com.frank.glyphify.glyph.composer.PatternFinder.light_anim_low_freq
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt
import kotlin.random.Random


class Glyphifier(private val context: Context, workerParams: WorkerParameters):
    Worker(context, workerParams) {

    private val path = context.filesDir.path
    private val uri = Uri.parse(inputData.getString("uri"))
    private val expanded = inputData.getBoolean("expanded", false)
    private var numZones = 5
    private val outName = inputData.getString("outputName")
    private val dimming_choice = inputData.getInt("dimming", -1)


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
        if (ext == "") throw RuntimeException(context.getString(R.string.error_no_file))

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
            throw RuntimeException(context.getString(R.string.error_failed_load))
        }

        // if the audio is not wav convert it using ffmpeg
        val session = FFmpegKit.execute("-i $path/$tmpName -ac 2 -ar 44100 $path/temp.wav -y")
        if(!ReturnCode.isSuccess(session.returnCode)){
            throw RuntimeException(context.getString(R.string.error_failed_conv_wav))
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
            val normalizedTime = (time + LIGHT_DURATION_US / 2) / LIGHT_DURATION_US * LIGHT_DURATION_US

            var normalizedEnergy: Int
            if(dimming_choice <= 0) normalizedEnergy = (energy * (GLYPH_MAX_INTENSITY / (2.0 * avgEnergy))).toInt()
            else normalizedEnergy = dimming_choice

            Pair(normalizedTime, normalizedEnergy)
        }
    }


    /**
     * Isolate beats that don't belong to a pattern and groups the others
     * @param beats: beats array
     * @return map with key "patterns" that contains all the found patterns and relative beats and
     * with key "beats" for beats not belonging to any pattern
     */
    fun isolatePatterns(beats: List<Pair<Int, Int>>): Map<String, Any> {
        val kmeansIndexes = clusterizeData(beats)

        val optimalPatterns: MutableList<List<List<Pair<Int, Int>>>> = mutableListOf()

        val pat = findPatterns(kmeansIndexes)
        if(pat.isNotEmpty()) {
            val res = findBestCombinationOfPatterns(kmeansIndexes, pat)

            for (pattern in res) {
                val len = pattern.first.size
                val patternBeatList = mutableListOf<List<Pair<Int, Int>>>()
                for (startingIndex in pattern.second) {
                    patternBeatList.add(beats.slice(startingIndex until startingIndex + len))
                }
                optimalPatterns.add(patternBeatList)
            }
        }

        // every other beat is treated as stand alone
        val otherBeats = beats.filter { beat -> !optimalPatterns.any { pattern -> pattern.flatten().contains(beat) } }

        return mapOf("patterns" to optimalPatterns, "beats" to otherBeats)
    }

    /**
     * Parses an animation string
     * @param animation: animation string
     * @return a list of lists, each list contains the zones to use
     * */
    private fun parseAnimationString(animation: String): List<List<Int>> {
        return animation.split("-").map { subElement ->
            when {
                subElement.contains("_") -> subElement.split("_").map { it.toInt() }
                subElement.contains(".") -> {
                    val range = subElement.split(".").map { it.toInt() }
                    (range[0]..range[1]).toList()
                }
                else -> listOf(subElement.toInt())
            }
        }
    }

    /**
     * Given a list of beats and a list of zones to use, it creates a new array with as many elements
     * as "numZones" and populates the specified zones with the light intensity specified by the
     * current beat, the other zones are left to value 0
     * @param beats: beats list
     * @param rndZone: list of zones to populate
     * @return the same beats list but expanded to use all specified zones
     * */
    fun processBeats(beats: List<Pair<Int, Int>>, rndZone: List<Int>): List<Pair<Int, List<Int>>> {
        return beats.map { pair ->
            val (timestamp, lightIntensity) = pair
            val newList = MutableList(numZones) { 0 }

            for (zones in rndZone) {
                newList[zones] = lightIntensity
            }

            Pair(timestamp, newList)
        }
    }

    /**
     * Adapts the fade out offset based on the chosen zone and the random choice for the light effect
     * @param zone: selected zone
     * @param effectChoice: random boolean use to chose the light effect
     * @return adjusted fade out offset
     * */
    private fun getFadeOutOffsetBasedOnZone(zone: Int, effectChoice: Boolean): Int {
        var fadeOutOffset = 0
        when(zone) {
            2 -> {
                if (numZones == 5) {
                    fadeOutOffset = 4
                }
            }

            3 -> {
                if (numZones == 11) {
                    fadeOutOffset = 4
                }
            }

            4 -> {
                if (numZones == 5) {
                    if (effectChoice) fadeOutOffset = 3
                }
                else if (numZones == 11) {
                    fadeOutOffset = 4
                }
            }

            in 5..8 -> {
                fadeOutOffset = 4
            }

            10 -> {
                if (effectChoice) fadeOutOffset = 3
            }
        }

        return fadeOutOffset
    }

    /**
     * Applies a light effect to a beat, it uses the selected zone and random choice of light effect
     * to find the best fade out offset while also adjusting the tempo if it's too fast and thus
     * avoid light flickering
     * @param beat
     * @param tempo: tempo of the freq band from which the beat was originated
     * @param zone: selected zone
     * @param effectChoice: random boolean use to chose the light effect
     * @return list of beats representing the light effect
     * */
    private fun getBeatWithEffectBasedOnSpeedAndZone(beat: Pair<Int, Int>, tempo: Double,
                                                     zone: Int, effectChoice: Boolean):
            List<Pair<Int, Int>> {

        var fadeOutOffset = getFadeOutOffsetBasedOnZone(zone, effectChoice)

        val adjustedTempo = if(tempo > 125.0) tempo / 2 else tempo
        val beatsSpeed = if(adjustedTempo > 100.0) 1 else 0

        var beatWithEffect: List<Pair<Int, Int>> = emptyList()
        when(beatsSpeed) {
            0 -> {
                beatWithEffect = if(effectChoice) expDecay(beat, adjustedTempo, fadeOutOffset)
                else circusTent(beat, adjustedTempo, fadeOutOffset)
            }
            1 -> {
                fadeOutOffset = (fadeOutOffset - 2).coerceAtLeast(0)
                beatWithEffect = if(effectChoice) expDecay(beat, adjustedTempo, fadeOutOffset)
                else circusTent(beat, adjustedTempo, fadeOutOffset)
            }
        }

        return beatWithEffect
    }

    /**
     * Merges beats together by finding the conflicting beats (same timestamp) and for each zone
     * selecting the highest value
     * @param beats: redundant beats list
     * @return merged and chronologically sorted beats list
     * */
    fun mergePairs(beats: List<Pair<Int, List<Int>>>): List<Pair<Int, List<Int>>> {
        return beats.groupBy { it.first }
            .map { (timestamp, pairList) ->
                val mergedList = pairList
                    .reduce { acc, pair ->
                        Pair(acc.first, acc.second.zip(pair.second) { a, b -> maxOf(a, b) })
                    }.second
                timestamp to mergedList
            }
            .sortedBy { it.first }
    }

    private fun randomizeAndTakeFirstN(initialList: List<Int>, lowerBound: Int, upperBound: Int):
            Pair<List<Int>, Int>{
        if(lowerBound >= initialList.size  || upperBound > initialList.size) return Pair(emptyList(), -1)

        val numRnds = Random.nextInt(lowerBound, upperBound)
        val rnds = initialList.shuffled().take(numRnds)
        val zoneIndexForBeatEffect = rnds.average().roundToInt()

        return Pair(rnds, zoneIndexForBeatEffect)
    }

    /**
     * Performs the separation of beats into patterns and normal beats, adds light effect in a random
     * zone for normal beats and applies a random animation for patterns, finally it merges
     * everything together
     * @param bandsBeats: list of beats in low freq and high freq
     * @param tempos: list of tempos in low freq and high freq
     * @return raw author tag data to be encoded
     * */
    private fun separatePatterns(bandsBeats: List<List<Pair<Int, Int>>>, tempos: List<Double>):
            List<Pair<Int, List<Int>>> {
        val tmp: MutableList<Pair<Int, List<Int>>> = mutableListOf()
        val maxAnimationUsageTimes = 3

        for ((index, bandBeats) in bandsBeats.withIndex()) {
            val separatedBeats = isolatePatterns(bandBeats)
            var beatWithEffect: List<Pair<Int, Int>>
            val tempo = tempos[index]

            val normalBeats = separatedBeats["beats"] as List<Pair<Int,Int>>
            for (beat in normalBeats) {

                var rnds: List<Int> = emptyList()
                var zoneIndexForBeatEffect: Int = 0
                var pair = Pair(rnds, zoneIndexForBeatEffect)
                when(numZones) {
                    3 -> {
                        val initList = (0..2).toList()
                        pair = randomizeAndTakeFirstN(initList, 1, 3)
                    }
                    5 -> {
                        // take 2 or 3 random glyphs and light them
                        val initList = (0..4).toList()
                        pair = randomizeAndTakeFirstN(initList, 2, 4)
                    }
                    11 -> {
                        // decide between central glyph and other ones first
                        var initList: List<Int> = emptyList()

                        val centralGlyph = Random.nextBoolean()
                        if(centralGlyph) {
                            initList = (3..8).toList()
                        }
                        else {
                            initList = (0..2).toList() + (9..10).toList()
                        }

                        pair = randomizeAndTakeFirstN(initList, 2, 4)
                    }
                }

                rnds = pair.first
                zoneIndexForBeatEffect = pair.second

                beatWithEffect = getBeatWithEffectBasedOnSpeedAndZone(beat, tempo, zoneIndexForBeatEffect, Random.nextBoolean())

                tmp.addAll(processBeats(beatWithEffect, rnds))

            }

            val patterns = separatedBeats["patterns"] as List<List<List<Pair<Int, Int>>>>
            for (pattern in patterns) {
                val patternLen = pattern[0].size
                val key = "$numZones.$patternLen"

                var listAnimations: List<String>?
                if(index == 0) {
                    listAnimations = light_anim_low_freq[key]
                }
                else {
                    listAnimations = light_anim_high_freq[key]
                }

                val patternNumRep = (pattern.size / maxAnimationUsageTimes) + 1
                var animation: MutableList<List<List<Int>>> = mutableListOf()

                for (i in 1..patternNumRep) {
                    val animationStr = listAnimations?.random()
                    if(animationStr != null) {
                        animation.add(parseAnimationString(animationStr))
                    }
                    else animation.add(emptyList())
                }

                for ((i, patternRep) in pattern.withIndex()) {
                    for ((j, beat) in patternRep.withIndex()) {
                        val effectChoice = Random.nextBoolean()
                        beatWithEffect = getBeatWithEffectBasedOnSpeedAndZone(beat, tempo, 2, effectChoice)
                        tmp.addAll(processBeats(beatWithEffect, animation[i/maxAnimationUsageTimes][j]))
                    }
                }
            }
        }

        return mergePairs(tmp)
    }

    /**
     * Expand the beats to work on the 33 zones of Phone(2)
     * @param beatsToExpand: beats map grouped by timestamp
     * @return expanded beats
     * */
    private fun expandTo33Zones(beatsToExpand: List<Pair<Int, List<Int>>>):
            List<Pair<Int, List<Int>>> {
        val expandedZones: MutableList<Pair<Int, List<Int>>> = mutableListOf()

        for ((timestamp, lightIntensities) in beatsToExpand) {
            var expandedList = lightIntensities.toMutableList()

            // fetch elements to expand
            val element3 = lightIntensities[3]
            val element9 = lightIntensities[9]

            // insert 15 copies of element 3 between elements 3 and 4
            for (i in 0 until 15) {
                expandedList.add(3, element3)
            }

            // adjust the index for element 9 after the previous insertions
            val newElement9Index = 9 + 15

            // insert 7 copies of element 9 between elements 9 and 10
            for (i in 0 until 7) {
                expandedList.add(newElement9Index, element9)
            }

            // swap last element with 24th for dot glyph
            val temp = expandedList[32]
            expandedList[32] = expandedList[24]
            expandedList[24] = temp

            expandedZones.add(Pair(timestamp, expandedList))
        }

        Log.d("DEBUG", expandedZones.toString())
        return expandedZones
    }

    /**
     * Expand the beats to work on the 33 zones of Phone(2)
     * @param beatsToExpand: beats map grouped by timestamp
     * @return expanded beats
     * */
    private fun expandTo26Zones(beatsToExpand: List<Pair<Int, List<Int>>>):
            List<Pair<Int, List<Int>>> {
        val expandedZones: MutableList<Pair<Int, List<Int>>> = mutableListOf()

        for ((timestamp, lightIntensities) in beatsToExpand) {
            var expandedList = lightIntensities.toMutableList()

            // fetch element to expand
            val element0 = lightIntensities[0]

            // insert 23 copies of element 0
            for (i in 0 until 23) {
                expandedList.add(0, element0)
            }

            expandedZones.add(Pair(timestamp, expandedList))
        }

        return expandedZones
    }

    /**
     * Build a Custom1 tag which shows the string 'GLIPHIFY' in the Composer app
     * @param audioLen: the duration of the audio in seconds
     * @return the compressed and encoded data for the preview
     * */
    private fun buildCustom1Tag(audioLen: Double): String {
        val ledsPattern = GLYPHIFY_COMPOSER_PATTERN.split(",")

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
    private fun buildAuthorTag(data: List<Pair<Int, List<Int>>>): String {

        val result = mutableListOf<List<Int>>()

        var currentTs = 0

        for (beats in data) {
            val nextTs = beats.first

            val numEmpty = (nextTs - currentTs) / LIGHT_DURATION_US - 1
            if (numEmpty > 0) {
                result.addAll(List(numEmpty) { MutableList(numZones) { 0 } })
            }

            result.add(beats.second)

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
    private fun buildOgg(outputName: String, custom1Tag: String, authorTag: String) {
        var sharedPref: SharedPreferences =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val appVers = sharedPref.getString("appVersion", "v1-Spacewar Glyph Composer")

        // convert from wav to ogg
        val session = FFmpegKit.execute("-i $path/temp.wav -c:a libopus " +
                "-metadata COMPOSER='$appVers' " +
                "-metadata TITLE='$ALBUM_NAME' " +
                "-metadata ALBUM='$ALBUM_NAME' " +
                "-metadata CUSTOM1='$custom1Tag' " +
                "-metadata CUSTOM2='$numZones'cols " +
                "-metadata AUTHOR='$authorTag' " +
                "\"$path/$outputName.ogg\" -y")

        if(!ReturnCode.isSuccess(session.returnCode)) throw RuntimeException(context.getString(R.string.error_failed_create_comp))

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
            ?: throw RuntimeException(context.getString(R.string.error_failed_exporting_comp))

        // save uri in the shared preferences so it can be later access to set the ringtone
        sharedPref = context.getSharedPreferences("URIS", Context.MODE_PRIVATE)
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
    }

    override fun doWork(): Result {
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_QUIET)
        if(Build.MODEL == PHONE2A_MODEL_ID) numZones = 3
        else numZones = 5

        try {
            // copy input file into app's filesystem and convert to wav
            prepareInput(uri)
            setProgressAsync(workDataOf("PROGRESS" to 5))

            // get audio duration and sample rate
            val audioInfo = getAudioDetails(context, "$path/temp.wav")

            // build custom1 GLYPHIFY text for composer app
            val custom1Tag = buildCustom1Tag(audioInfo.first)

            setProgressAsync(workDataOf("PROGRESS" to 15))

            // build led animation based on selected song
            val rawBeats = BeatDetector.detectBeatsAndFrequencies(context, path, "temp.wav")
            setProgressAsync(workDataOf("PROGRESS" to 30))

            val normalizedBandsBeats = rawBeats.second.map { normalizeBeats(it) }
            setProgressAsync(workDataOf("PROGRESS" to 35))

            if(expanded) numZones = 11
            var animatedBeats = separatePatterns(normalizedBandsBeats, rawBeats.first)
            setProgressAsync(workDataOf("PROGRESS" to 70))

            if(expanded) {      // if device is Phone(2), expand to 33 zones
                animatedBeats = expandTo33Zones(animatedBeats)
                numZones = 33
                setProgressAsync(workDataOf("PROGRESS" to 75))
            }
            else if(numZones == 3) {
                animatedBeats = expandTo26Zones(animatedBeats)
                numZones = 26
                setProgressAsync(workDataOf("PROGRESS" to 75))
            }

            val authorTag = buildAuthorTag(animatedBeats)
            if (authorTag == "") return Result.failure()

            setProgressAsync(workDataOf("PROGRESS" to 80))

            // create ogg file which contains the Glyphified song
            if(outName == null) throw RuntimeException(context.getString(R.string.error_no_output_name))
            buildOgg(outName, custom1Tag, authorTag)

            setProgressAsync(workDataOf("PROGRESS" to 100))
            Thread.sleep(100)   // needed to show the progress bar at 100%
        }
        catch (e: Exception) {
            return Result.failure(workDataOf("ERROR_MESSAGE" to e.message))
        }

        return Result.success()
    }
}