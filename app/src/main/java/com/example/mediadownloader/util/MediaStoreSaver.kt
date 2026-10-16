package com.example.mediadownloader.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.mediadownloader.model.DownloadFormat
import java.io.File
import java.io.FileInputStream

object MediaStoreSaver {
    private const val TAG = "MediaStoreSaver"

    /**
     * Sanitizes a title string so it can be safely used as a filename.
     */
    fun sanitizeFileName(title: String, defaultName: String = "media"): String {
        val sanitized = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return sanitized.ifBlank { defaultName }
    }

    /**
     * Saves a temporary downloaded file into the Android MediaStore (Gallery / Music library).
     * Automatically chooses Video (Movies/Downloader) or Audio (Music/Downloader).
     */
    fun saveToGallery(context: Context, tempFile: File, title: String, format: DownloadFormat): Uri? {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            Log.e(TAG, "Source file does not exist or is empty: ${tempFile.absolutePath}")
            return null
        }

        val cleanTitle = sanitizeFileName(title)
        val fileName = "$cleanTitle.${format.extension}"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStoreModern(context, tempFile, fileName, format)
            } else {
                saveViaPublicDirectoryLegacy(context, tempFile, cleanTitle, fileName, format)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore / Gallery: ${e.message}", e)
            null
        } finally {
            // Clean up temporary cache file
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (delEx: Exception) {
                Log.w(TAG, "Failed to delete temporary file: ${delEx.message}")
            }
        }
    }

    private fun saveViaMediaStoreModern(
        context: Context,
        tempFile: File,
        fileName: String,
        format: DownloadFormat
    ): Uri? {
        val resolver = context.contentResolver
        val (collection, relativePath) = when (format) {
            DownloadFormat.MP4 -> Pair(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${Environment.DIRECTORY_MOVIES}/Downloader"
            )
            DownloadFormat.MP3 -> Pair(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${Environment.DIRECTORY_MUSIC}/Downloader"
            )
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(collection, contentValues) ?: return null

        try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                FileInputStream(tempFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                }
            } ?: run {
                Log.e(TAG, "Failed to open output stream for $itemUri")
                resolver.delete(itemUri, null, null)
                return null
            }

            // Publish the file to MediaStore and Gallery
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)

            Log.i(TAG, "Successfully saved $fileName to gallery: $itemUri")
            return itemUri
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }
    }

    private fun saveViaPublicDirectoryLegacy(
        context: Context,
        tempFile: File,
        cleanTitle: String,
        fileName: String,
        format: DownloadFormat
    ): Uri? {
        val publicDirType = if (format == DownloadFormat.MP4) {
            Environment.DIRECTORY_MOVIES
        } else {
            Environment.DIRECTORY_MUSIC
        }
        val targetDir = File(Environment.getExternalStoragePublicDirectory(publicDirType), "Downloader").apply {
            mkdirs()
        }

        var destFile = File(targetDir, fileName)
        if (destFile.exists()) {
            destFile = File(targetDir, "${cleanTitle}_${System.currentTimeMillis()}.${format.extension}")
        }

        tempFile.copyTo(destFile, overwrite = true)

        // Scan the actual destination file so it appears immediately in the gallery
        var finalUri: Uri? = null
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(format.mimeType)
            ) { _, uri ->
                if (uri != null) {
                    finalUri = uri
                }
            }
        } catch (scanEx: Exception) {
            Log.w(TAG, "MediaScanner exception: ${scanEx.message}")
        }

        return finalUri ?: Uri.fromFile(destFile)
    }
}
