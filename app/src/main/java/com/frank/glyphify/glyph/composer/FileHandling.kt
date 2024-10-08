package com.frank.glyphify.glyph.composer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.arthenica.ffmpegkit.FFprobeKit
import com.frank.glyphify.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.Inflater

object FileHandling {
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null, null)
        var filename = ""

        try {
            if (cursor != null && cursor.moveToFirst()) {
                val filenameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if(filenameColumnIndex != -1){
                    filename = cursor.getString(filenameColumnIndex)
                }
                cursor.close()
                return filename
            }
        }
        catch (e: Exception) {
            return filename
        }

        return filename
    }

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
    fun getAudioDetails(context: Context, filePath: String): Pair<Double, Int> {
        try {
            val mediaInformation = FFprobeKit.getMediaInformation(filePath).mediaInformation
            val duration = mediaInformation.duration.toDouble()
            if(duration > 300) throw RuntimeException(context.getString(R.string.error_duration_long))

            val streams = mediaInformation.streams
            for (stream in streams) {
                if (stream.type == "audio") {
                    val sampleRate = stream.sampleRate.toInt()
                    return Pair(duration, sampleRate)
                }
            }

            throw RuntimeException(context.getString(R.string.error_invalid_audio_file))
        }
        catch (e: RuntimeException) {
            throw e
        }
    }

    /**
     * Compress data using zlib and then encodes it in base64
     * @param data: the data to work on
     * @return a string containing the base64 representation of the compresses data
     * */
    fun compressAndEncode(data: String): String {
        val input = data.toByteArray(Charsets.UTF_8)

        // compress the bytes
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

        // encode to Base64
        var base64Data = Base64.encodeToString(compressedBytes, Base64.DEFAULT)

        // remove padding bytes
        base64Data = base64Data.trimEnd('=')

        // add newline every 76 characters
        val formattedData = base64Data.chunked(76).joinToString("\n")

        return "$formattedData\n"
    }

    /**
     * Decodes a base64 string and then decompresses it using zlib
     * @param base64Data: the base64 encoded and compressed data
     * @return a string containing the original data
     */
    fun decodeAndDecompress(data: String): String {
        // Remove newline characters
        val base64Data = data.replace("\n", "")

        // Add padding bytes
        val padding = "=".repeat((4 - base64Data.length % 4) % 4)
        val paddedBase64Data = base64Data + padding

        // Decode from Base64
        val compressedBytes = Base64.decode(paddedBase64Data, Base64.DEFAULT)

        // Decompress the bytes
        val inflater = Inflater()
        inflater.setInput(compressedBytes)

        val outputStream = ByteArrayOutputStream(compressedBytes.size)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        val decompressedBytes = outputStream.toByteArray()

        return String(decompressedBytes, Charsets.UTF_8)
    }

}