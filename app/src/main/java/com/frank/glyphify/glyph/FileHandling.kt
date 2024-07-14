package com.frank.glyphify.glyph

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import com.arthenica.ffmpegkit.FFprobeKit
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.Deflater

object FileHandling {
    fun getFileExtension(uri: Uri, contentResolver: ContentResolver): String {
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
    fun getAudioDetails(filePath: String): Pair<Double, Int> {
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

}