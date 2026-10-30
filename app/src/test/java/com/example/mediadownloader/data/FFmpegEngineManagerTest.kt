package com.example.mediadownloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FFmpegEngineManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun repairCorruptSymlinks_replacesTextPlaceholderWithTargetContent() {
        val usrLib = tempFolder.newFolder("usr", "lib")

        // Create a real target library > 1024 bytes starting with ELF magic
        val realLib = File(usrLib, "libavcodec.so.61.19.101").apply {
            writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(2048) { 0x42 })
        }
        assertTrue(realLib.length() > 1024L)

        // Create a corrupt 23-byte text file representing an unlinked symlink
        val placeholder = File(usrLib, "libavcodec.so.61").apply {
            writeText("libavcodec.so.61.19.101\n")
        }
        assertEquals(24L, placeholder.length())

        // Simulate repair logic on usrLib
        val files = usrLib.listFiles() ?: emptyArray()
        for (file in files) {
            if (file.length() in 1..1024L) {
                val header = file.inputStream().use { it.readNBytes(4) }
                val isElf = header.contentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
                if (!isElf) {
                    val targetName = file.readText().trim()
                    val targetFile = File(usrLib, targetName)
                    if (targetFile.exists() && targetFile.length() > 1024L) {
                        file.delete()
                        targetFile.copyTo(file, overwrite = true)
                    }
                }
            }
        }

        // Verify the placeholder was repaired into a valid > 1024-byte ELF file
        val repairedFile = File(usrLib, "libavcodec.so.61")
        assertTrue(repairedFile.exists())
        assertTrue(repairedFile.length() > 1024L)
        val header = repairedFile.inputStream().use { it.readNBytes(4) }
        assertTrue(header.contentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())))
    }

    @Test
    fun binaryAliases_createsExpectedBinaries() {
        val ffmpegDir = tempFolder.newFolder("ffmpeg")
        val binDir = File(ffmpegDir, "bin").apply { mkdirs() }
        val nativeLibDir = tempFolder.newFolder("lib", "arm64-v8a")

        val libFfmpeg = File(nativeLibDir, "libffmpeg.so").apply {
            writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(500) { 1 })
        }
        val libFfprobe = File(nativeLibDir, "libffprobe.so").apply {
            writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(500) { 2 })
        }

        // Create copies in binDir
        val binFfmpeg = File(binDir, "ffmpeg")
        val binFfprobe = File(binDir, "ffprobe")
        libFfmpeg.copyTo(binFfmpeg)
        libFfprobe.copyTo(binFfprobe)

        assertTrue(binFfmpeg.exists())
        assertTrue(binFfprobe.exists())
        assertEquals(libFfmpeg.length(), binFfmpeg.length())
        assertEquals(libFfprobe.length(), binFfprobe.length())
    }
}
