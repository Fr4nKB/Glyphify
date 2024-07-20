package com.frank.glyphify.glyph.composer

import android.util.Log
import smile.clustering.KMeans

object PatternFinder {

    val light_anim_low_freq: Map<String, List<String>> = mapOf(
        "5.3" to listOf(
            "0_2-2-1_2",
            "1_2-2-0_2",

            "0_1_4-0_1_2_4-0_1_4",
            "2_3-0.2-2_3",
            "3-2_3-3",

            "0_2_4-3-2",
            "1_2_4-3-2",
            "0_1-2-0_1",
            "0_1-2-0_1"
        ),
        "5.4" to listOf(
            "3-3_4-0_1_4-2",
            "2-0_1-4_3-3",

            "0-2-2_3-3_4",
            "1-2-2_3-3_4",
            "0_1_4-3-2-0_1_2",

            "0-1-0-1",
            "3_4-3-3_4-3",
            "0.4-2-0.4-2",
            "3_4-3-2-0_1"
        ),
        "5.5" to listOf(
            "1-2-3-2-0_2",
            "0-2-3-2-1_2",

            "3_4-3_4-2-1-0_2",
            "1_2-2-0-2-1_2",
            "0.4-2-0.4-0_1_4-2",

            "0_2-1-2-3-0_2_3_4",
            "3_4-3-2-0-1_2",
            "0_2-1-2_4-1-0_2",
            "3-2-2_4-2.4-0.4"
        ),

        // only use central glyph
        "11.3" to listOf(
            "6.8-5_6-3_4_5",
            "3_6-4_7-5_8",
            "5_8-4_7-3_6",
            "6.8-3.5-3.8",
            "3_6-3_4_6_7-3.8",
            "5_8-3_5_6_8-3.8",
            "4_7-4_5_7_8-3_4_6_7"
        ),
        "11.4" to listOf(
            "3_4-3_4_5_8-5.8-6_7",
            "3.8-3_5_7-3.8-4_6_8",
            "4_7-4_5_7_8-4_7-3_4_6_7",
            "3.5-4.6-5.7-6.8",
            "4_8-4_5_8-5_6_8-6_8",
            "3_4_6_7-3_5_6_8-3_4_6_7-4_5_7_8"
        ),
        "11.5" to listOf(
            "7_8-6_7-5_6-4_5-3_4",

            "3_5_7-4_6_8-3_5_7-4_6_8-3.8",
            "6.8-5.7-4.6-3.5-3_4_8",
            "6_7-6.8-3_6_7_8-3_4_6_7_8-3_4_5_7_8",

            "3_4_5_7_8-3_4_6_7_8-3_6_7_8-6.8-6_7"
        )
    )

    val light_anim_high_freq: Map<String, List<String>> = mapOf(
        "5.3" to listOf(
            "0-2-1",
            "1-2-0",

            "0_1_4-0_1_4-0_1_4",
            "2-2-2",
            "3-3-3",

            "4-3-2",
            "4-3-2",
            "0_1-2-0_1",
            "0_1-2-0_1"
        ),
        "5.4" to listOf(
            "3-4-0_1-2",
            "2-0_1-4-3",

            "0-2-3-4",
            "1-2-3-4",
            "4-3-2-0_1",

            "0-1-0-1",
            "4-3-4-3",
            "0.4-2-0.4-2",
            "4-3-2-0_1"
        ),
        "5.5" to listOf(
            "1-2-3-2-0",
            "0-2-3-2-1",

            "3_4-3_4-2-1-0",
            "1-2-0-2-1",
            "0.4-2-0.4-4-2",

            "0-1-2-3-4",
            "4-3-2-0-1",
            "0-1-4-1-0",
            "3-2-2_4-2.4-0.4"
        ),

        "11.3" to listOf(
            "0-0_1-0.2",
            "0_1_9_10-0_2_9_10-2_9_10",
            "9-9_10-0_1_2_9_10",
        ),
        "11.4" to listOf(
            "0.5-6.10-0.2-9_10-0.10",
            "0.5-6.10-3.8-0_1_2_9_10",
            "9_10-5_6_9_10-9_10-7.10",
        ),
        "11.5" to listOf(
            "2-0.2-0_1_9_10-9_10-2_9_10",
            "0_1_2_9_10-0_2_10-0_1_2_9_10-1_2_10-9",
            "0.5-6.10-0.2-9_10-0.10",
        )
    )

    /**
     * Converts the array of beats into a delta array where timestamp are expressed in terms of the
     * delta with the previous timestamp, then performs Kmeans and returns the cluster indexes of each
     * beat
     * @param beats: beats array
     * @return array of cluster indexes
     * */
    fun clusterizeData(beats: List<Pair<Int, Int>>): IntArray {
        // represent each beat's timestamp as the delta w.r.t. the previous one
        val deltaBeats = ArrayList<Array<Double>>()
        var prev = beats[0].first
        for (elem in beats) {
            deltaBeats.add(arrayOf((elem.first - prev).toDouble(), elem.second.toDouble()))
            prev = elem.first
        }

        // convert to 2D array to be used in kmeans
        val deltaBeats2D = deltaBeats.map { it.toDoubleArray() }.toTypedArray()
        val kmeans = KMeans.fit(deltaBeats2D, 10, 1000, 0.0001)

        return kmeans.y // return indexes
    }

    /**
     * Extracts all non overlapping and repeating (>1) patterns from an array of int values
     * @param data: int array to analyze
     * @return a map containing all the repeating overlapping patterns of length 3 to 5
     * */
    fun findPatterns(data: IntArray): Map<Int, MutableSet<Pair<IntArray, IntArray>>> {
        val patterns = mutableMapOf<List<Int>, MutableMap<String, Any>>()
        for (i in 3..5) {  // search for overlapping patterns of length 3 to 5
            for (j in 0 until data.size - i + 1) {
                val pattern = data.slice(j until j+i)
                if (pattern !in patterns) {
                    patterns[pattern] = mutableMapOf("count" to 0, "indexes" to mutableListOf<Int>())
                }
                val patternInfo = patterns[pattern]!!
                patternInfo["count"] = patternInfo["count"] as Int + 1
                (patternInfo["indexes"] as MutableList<Int>).add(j)
            }
        }

        val patternsByLength = mutableMapOf<Int, MutableSet<Pair<IntArray, IntArray>>>()
        for ((pattern, patternInfo) in patterns) {
            val length = pattern.size
            if (length !in patternsByLength) {
                patternsByLength[length] = mutableSetOf()
            }
            if (patternInfo["count"] as Int > 1) {
                patternsByLength[length]?.add(Pair(pattern.toIntArray(), (patternInfo["indexes"] as List<Int>).toIntArray()))
            }
        }

        patternsByLength.getOrPut(3) { mutableSetOf() }.add(Pair(IntArray(3) { -1 }, IntArray(0)))
        patternsByLength.getOrPut(4) { mutableSetOf() }.add(Pair(IntArray(4) { -1 }, IntArray(0)))
        patternsByLength.getOrPut(5) { mutableSetOf() }.add(Pair(IntArray(5) { -1 }, IntArray(0)))

        return patternsByLength
    }

    /**
     * Returns a measure of similarity for two arrays by comparing values one by one
     * @param arr1
     * @param arr2
     * @return a value between 0 and arr1.size (== arr2.size) representing low and high similarity
     * respectively
     * */
    fun getSimilarity(arr1: IntArray, arr2: IntArray): Int {
        if (arr1.size != arr2.size) {
            return 0
        }
        return arr1.zip(arr2).count { it.first == it.second }
    }

    /**
     * Tries to place a pattern at the right index in arr1, the pattern is inserted as many times as
     * possible. A pattern cannot be placed if any of the values where it should be placed is -1.
     * If the pattern can be placed only once then it doesn't get placed.
     * @param arr
     * @param pattern: a pair of [values to insert], [starting indexes where the values should be inserted]
     * @return the new populated array and the indexes at which the pattern was placed
     * */
    fun placePattern(arr: IntArray, pattern: Pair<IntArray, IntArray>): Pair<IntArray, IntArray> {
        val patt = pattern.first
        val pattLen = patt.size
        val insertableElems = pattern.second.filter { index ->
            (index until index + pattLen).all { i -> arr[i] == -1 }
        }.toIntArray()

        if (insertableElems.size > 1) {
            for (index in insertableElems) {
                for (i in 0 until pattLen) {
                    arr[index + i] = patt[i]
                }
            }
        }

        return Pair(arr, insertableElems)
    }

    fun loopPatterns(data: IntArray, pattern: Pair<IntArray, IntArray>,
                     bestSimilarityComb: IntArray, bestSimilarity: Int,
                     patternsUsed: MutableList<Pair<IntArray, IntArray>>, reset: Boolean):
            Triple<IntArray, Int, MutableList<Pair<IntArray, IntArray>>> {

        var tmp = bestSimilarityComb
        var bestSimilarityLocal = bestSimilarity
        var bestSimilarityCombLocal = bestSimilarityComb
        var patternsUsedLocal = patternsUsed

        val result = placePattern(tmp, pattern)
        tmp = result.first
        val insertedIndexes = result.second

        val tmpSim = getSimilarity(tmp, data)
        if (tmpSim > bestSimilarityLocal) {
            bestSimilarityLocal = tmpSim
            bestSimilarityCombLocal = tmp
            if (reset) {
                patternsUsedLocal.clear()
            }
            patternsUsedLocal.add(Pair(pattern.first, insertedIndexes))
        }

        return Triple(bestSimilarityCombLocal, bestSimilarityLocal, patternsUsedLocal)
    }

    /**
     * Finds the best combinations of non overlapping patterns among the identified repetitive and
     * overlapping patterns. The goal is to maximize the amount of patterns to cover as much as possible
     * of the clusters indexes array. This clearly doesn't scale well, a better solution should be found
     * but if the audio is not that long then it won't be a problem.
     * @param data: array from which the patterns were extracted
     * @param patterns: all identified repetitive and overlapping patterns
     * @return the optimal list of which patterns should be used and at what indices to maximize coverage
     * */
    fun findBestCombinationOfPatterns(data: IntArray, patterns: Map<Int,
            MutableSet<Pair<IntArray, IntArray>>>):
            MutableList<Pair<IntArray, IntArray>> {

        val dataLen = data.size
        var bestSimilarity = 0
        var bestSimilarityComb = IntArray(dataLen) { -1 }
        var patternsUsed = mutableListOf<Pair<IntArray, IntArray>>()

        for (patt5 in patterns[5]!!) {
            val result = loopPatterns(data, patt5, bestSimilarityComb, bestSimilarity, patternsUsed, true)
            bestSimilarityComb = result.first
            bestSimilarity = result.second
            patternsUsed = result.third

            for (patt4 in patterns[4]!!) {
                val result = loopPatterns(data, patt4, bestSimilarityComb, bestSimilarity, patternsUsed, false)
                bestSimilarityComb = result.first
                bestSimilarity = result.second
                patternsUsed = result.third

                for (patt3 in patterns[3]!!) {
                    val result = loopPatterns(data, patt3, bestSimilarityComb, bestSimilarity, patternsUsed, false)
                    bestSimilarityComb = result.first
                    bestSimilarity = result.second
                    patternsUsed = result.third
                }
            }
        }

        return patternsUsed
    }

}