package com.example.mediadownloader.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object FFmpegEngineManager {
    private const val TAG = "FFmpegManager"
    private val FFMPEG_RELEASE_URLS = listOf(
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.4/ffmpeg-arm64-v8a.zip",
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.3/ffmpeg-arm64-v8a.zip",
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.2/ffmpeg-arm64-v8a.zip",
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.1/ffmpeg-arm64-v8a.zip",
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.0/ffmpeg-arm64-v8a.zip"
    )

    private val mutex = Mutex()

    private fun logI(tag: String, message: String) {
        try {
            android.util.Log.i(tag, message)
        } catch (_: Throwable) {
            println("[$tag] $message")
        }
    }

    private fun logW(tag: String, message: String) {
        try {
            android.util.Log.w(tag, message)
        } catch (_: Throwable) {
            println("[$tag] $message")
        }
    }

    private fun logE(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        } catch (_: Throwable) {
            System.err.println("[$tag] $message")
            throwable?.printStackTrace()
        }
    }

    fun getFFmpegDir(context: Context): File {
        val base = File(context.applicationContext.noBackupFilesDir, "youtubedl-android")
        val packages = File(base, "packages")
        return File(packages, "ffmpeg")
    }

    fun isInstalled(context: Context): Boolean {
        val dir = getFFmpegDir(context)
        if (!dir.exists() || !dir.isDirectory) return false
        val libDir = File(dir, "usr/lib")
        if (!libDir.exists() || !libDir.isDirectory) return false

        // Check key media libraries exist and have genuine ELF binaries
        val keyLibraries = listOf("libavcodec", "libavformat", "libavutil", "libswscale", "libswresample")
        val files = libDir.listFiles() ?: return false
        for (key in keyLibraries) {
            val matching = files.filter { it.name.startsWith(key) }
            if (matching.isEmpty() || !matching.any { isValidElf(it) || it.length() > 50 * 1024L }) {
                return false
            }
        }

        // Must not contain unresolved corrupt placeholders
        val corruptPlaceholders = libDir.walkTopDown().filter { it.isFile && isCorruptPlaceholder(it) }.count()
        if (corruptPlaceholders > 0) {
            return false
        }

        // Check binDir contains valid ffmpeg and ffprobe aliases, or create them from nativeLibraryDir
        val binDir = File(dir, "bin")
        val binFfmpeg = File(binDir, "ffmpeg")
        val binFfprobe = File(binDir, "ffprobe")
        if (!binFfmpeg.exists() || binFfmpeg.length() == 0L || !binFfprobe.exists() || binFfprobe.length() == 0L) {
            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            val nativeFfmpeg = File(nativeDir, "libffmpeg.so")
            val nativeFfprobe = File(nativeDir, "libffprobe.so")
            if (nativeFfmpeg.exists() && nativeFfprobe.exists()) {
                try {
                    binDir.mkdirs()
                    setupBinaryAlias(nativeFfmpeg, binFfmpeg)
                    setupBinaryAlias(nativeFfprobe, binFfprobe)
                } catch (_: Throwable) {}
            }
            if (!binFfmpeg.exists() || binFfmpeg.length() == 0L || !binFfprobe.exists() || binFfprobe.length() == 0L) {
                return false
            }
        }

        return true
    }

    fun getFFmpegLocation(context: Context): String {
        val targetDir = getFFmpegDir(context)
        val binDir = File(targetDir, "bin")
        if (File(binDir, "ffmpeg").exists() && File(binDir, "ffprobe").exists()) {
            return binDir.absolutePath
        }
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val libFfmpeg = File(nativeDir, "libffmpeg.so")
        if (libFfmpeg.exists()) {
            return libFfmpeg.absolutePath
        }
        return targetDir.absolutePath
    }

    fun isValidElf(file: File): Boolean {
        if (!file.exists() || file.isDirectory || file.length() < 4) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 &&
                        header[0] == 0x7F.toByte() &&
                        header[1] == 'E'.code.toByte() &&
                        header[2] == 'L'.code.toByte() &&
                        header[3] == 'F'.code.toByte()
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isCorruptPlaceholder(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
        if (isSymlink(file)) return false
        if (file.length() in 1..1024L) {
            if (isValidElf(file)) return false
            val lower = file.name.lowercase()
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".pc") ||
                lower.endsWith(".h") || lower.endsWith(".c") || lower.endsWith(".json") ||
                lower.endsWith(".xml") || lower.endsWith(".version") || lower.endsWith(".cmake") ||
                lower.endsWith(".pc.in") || lower.endsWith(".conf") || lower == "license") {
                return false
            }
            val content = try {
                file.readText().trim()
            } catch (_: Throwable) { "" }
            if (content.isEmpty() || content.length > 255 || content.contains('\n') || content.contains('\r') || content.contains(' ')) {
                return false
            }
            return true
        }
        return false
    }

    fun resolveSymlinkTarget(file: File, baseDir: File, maxHops: Int = 10): File? {
        var current = file
        var hops = 0
        val visited = mutableSetOf<String>()
        val startCanonical = try { current.canonicalPath } catch (_: Throwable) { current.absolutePath }
        visited.add(startCanonical)

        while (hops < maxHops) {
            if (!isCorruptPlaceholder(current)) {
                if (isValidElf(current) || (current.exists() && current.length() > 1024L)) {
                    return current
                }
                return null
            }

            val targetName = try {
                val text = current.readText().trim()
                if (text.isEmpty() || text.length > 255 || text.contains('\n') || text.contains('\r') || text.contains(' ')) {
                    ""
                } else {
                    text
                }
            } catch (_: Throwable) { "" }

            if (targetName.isEmpty()) return null

            val nextFile = try {
                File(current.parentFile ?: baseDir, targetName).canonicalFile
            } catch (_: Throwable) {
                null
            } ?: return null

            if (!nextFile.exists()) return null
            val nextCanonical = try { nextFile.canonicalPath } catch (_: Throwable) { nextFile.absolutePath }
            if (!visited.add(nextCanonical)) {
                // Cycle detected in symlink chain
                return null
            }
            current = nextFile
            hops++
        }
        return null
    }

    fun repairDirectory(dir: File): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        var totalRepaired = 0
        var modifiedInPass = true
        var passes = 0

        while (modifiedInPass && passes < 10) {
            modifiedInPass = false
            passes++

            val files = try {
                dir.walkTopDown().filter { it.isFile && isCorruptPlaceholder(it) }.toList()
            } catch (t: Throwable) {
                logW(TAG, "Directory walk failed: ${t.message}")
                emptyList()
            }

            for (file in files) {
                try {
                    val realTarget = resolveSymlinkTarget(file, dir)
                    if (realTarget != null && (isValidElf(realTarget) || realTarget.length() > 1024L)) {
                        file.delete()
                        var linked = false
                        try {
                            val relPath = if (file.parentFile == realTarget.parentFile) {
                                realTarget.name
                            } else {
                                realTarget.relativeTo(file.parentFile ?: dir).path.replace('\\', '/')
                            }
                            if (relPath.length <= 255 && !relPath.contains('\n') && !relPath.contains('\r')) {
                                android.system.Os.symlink(relPath, file.absolutePath)
                                linked = file.exists()
                            }
                        } catch (t: Throwable) {
                            logW(TAG, "Symlink failed for ${file.name} -> ${realTarget.name}: ${t.message}")
                        }
                        if (!linked) {
                            try {
                                realTarget.copyTo(file, overwrite = true)
                            } catch (t: Throwable) {
                                logE(TAG, "Copy fallback failed for ${file.name}: ${t.message}")
                            }
                        }
                        totalRepaired++
                        modifiedInPass = true
                    }
                } catch (t: Throwable) {
                    logW(TAG, "Failed repairing placeholder ${file.name}: ${t.message}")
                }
            }
        }
        return totalRepaired
    }

    fun repairSymlinksAndBinaries(context: Context) {
        val targetDir = getFFmpegDir(context)
        try {
            if (targetDir.exists() && targetDir.isDirectory) {
                repairDirectory(targetDir)
            }
        } catch (t: Throwable) {
            logW(TAG, "FFmpeg directory repair exception: ${t.message}")
        }

        // Setup bin directory and binary aliases
        try {
            val binDir = File(targetDir, "bin").apply { mkdirs() }
            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            val nativeFfmpeg = File(nativeDir, "libffmpeg.so")
            val nativeFfprobe = File(nativeDir, "libffprobe.so")

            if (nativeFfmpeg.exists()) {
                setupBinaryAlias(nativeFfmpeg, File(binDir, "ffmpeg"))
                setupBinaryAlias(nativeFfmpeg, File(targetDir, "ffmpeg"))
                setupBinaryAlias(nativeFfmpeg, File(targetDir, "libffmpeg.so"))
            }

            if (nativeFfprobe.exists()) {
                setupBinaryAlias(nativeFfprobe, File(binDir, "ffprobe"))
                setupBinaryAlias(nativeFfprobe, File(targetDir, "ffprobe"))
                setupBinaryAlias(nativeFfprobe, File(targetDir, "libffprobe.so"))
            }
        } catch (t: Throwable) {
            logW(TAG, "Failed creating binary aliases: ${t.message}")
        }
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            val stat = android.system.Os.lstat(file.absolutePath)
            android.system.OsConstants.S_ISLNK(stat.st_mode)
        } catch (_: Throwable) {
            try {
                java.nio.file.Files.isSymbolicLink(file.toPath())
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun setupBinaryAlias(source: File, destination: File) {
        if (destination.exists()) {
            if (destination.length() == 0L || isCorruptPlaceholder(destination)) {
                destination.delete()
            } else {
                destination.setExecutable(true, false)
                return
            }
        }
        var created = false
        try {
            android.system.Os.symlink(source.absolutePath, destination.absolutePath)
            created = destination.exists()
        } catch (_: Throwable) {}
        if (!created) {
            try {
                source.copyTo(destination, overwrite = true)
            } catch (_: Throwable) {}
        }
        destination.setExecutable(true, false)
    }

    suspend fun ensureFFmpegInstalled(
        context: Context,
        onProgress: ((percent: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            repairSymlinksAndBinaries(context)
            if (isInstalled(context)) return@withContext true

            mutex.withLock {
                repairSymlinksAndBinaries(context)
                if (isInstalled(context)) return@withLock true

                val targetDir = getFFmpegDir(context)
                targetDir.mkdirs()

                val tempZip = File(context.cacheDir, "ffmpeg_download.tmp")

                for (ffmpegUrl in FFMPEG_RELEASE_URLS) {
                    try {
                        logI(TAG, "Downloading FFmpeg native engine package from $ffmpegUrl")
                        val url = URL(ffmpegUrl)
                        var connection = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 30000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "SnapLoad-Android/3.0.4")
                        }

                        var responseCode = connection.responseCode
                        var redirects = 0
                        while (responseCode in 300..399 && redirects < 5) {
                            val redirectUrl = connection.getHeaderField("Location")
                            if (redirectUrl.isNullOrBlank()) break
                            val currentUrl = connection.url
                            connection.disconnect()
                            connection = (URL(currentUrl, redirectUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 15000
                                readTimeout = 30000
                                instanceFollowRedirects = true
                                setRequestProperty("User-Agent", "SnapLoad-Android/3.0.4")
                            }
                            responseCode = connection.responseCode
                            redirects++
                        }

                        if (responseCode !in 200..299) {
                            throw java.io.IOException("FFmpeg download failed with HTTP code $responseCode")
                        }

                        val totalBytes = connection.contentLengthLong
                        var downloadedBytes = 0L

                        BufferedInputStream(connection.inputStream).use { input ->
                            FileOutputStream(tempZip).use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var bytesRead: Int
                                var lastReported = -1

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    if (totalBytes > 0) {
                                        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                        if (percent != lastReported) {
                                            lastReported = percent
                                            onProgress?.invoke(percent)
                                        }
                                    }
                                }
                            }
                        }

                        logI(TAG, "Extracting FFmpeg to ${targetDir.absolutePath}...")
                        unzip(tempZip, targetDir)
                        tempZip.delete()

                        repairSymlinksAndBinaries(context)

                        val success = isInstalled(context)
                        logI(TAG, "FFmpeg installed successfully: $success")
                        if (success) return@withLock true
                    } catch (e: Exception) {
                        logW(TAG, "Failed downloading FFmpeg from $ffmpegUrl: ${e.message}")
                        if (tempZip.exists()) tempZip.delete()
                    }
                }
                repairSymlinksAndBinaries(context)
                isInstalled(context)
            }
        } catch (t: Throwable) {
            logE(TAG, "Unexpected error in ensureFFmpegInstalled: ${t.message}", t)
            false
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                val canonicalDestPath = destDir.canonicalPath
                val canonicalEntryPath = newFile.canonicalPath
                if (canonicalEntryPath != canonicalDestPath && !canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    throw SecurityException("Zip traversal attempt: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
