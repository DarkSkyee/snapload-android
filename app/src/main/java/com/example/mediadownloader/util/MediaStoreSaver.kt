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
        val resolver = context.contentResolver

        val (collection, relativePath, mimeType) = when (format) {
            DownloadFormat.MP4 -> {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                Triple(collection, "${Environment.DIRECTORY_MOVIES}/Downloader", format.mimeType)
            }
            DownloadFormat.MP3 -> {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                Triple(collection, "${Environment.DIRECTORY_MUSIC}/Downloader", format.mimeType)
            }
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        var itemUri: Uri? = null
        try {
            itemUri = resolver.insert(collection, contentValues)
            if (itemUri == null) {
                Log.e(TAG, "Failed to insert MediaStore item for $fileName")
                return null
            }

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            // Also trigger media scanner to ensure instant indexing by system gallery
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(tempFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            } catch (scanEx: Exception) {
                Log.w(TAG, "Scan failed (harmless on modern Android): ${scanEx.message}")
            }

            // Delete temporary cache file to free space
            tempFile.delete()
            Log.i(TAG, "Successfully saved $fileName to gallery: $itemUri")
            return itemUri

        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore: ${e.message}", e)
            itemUri?.let { uri ->
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            return null
        }
    }
}
