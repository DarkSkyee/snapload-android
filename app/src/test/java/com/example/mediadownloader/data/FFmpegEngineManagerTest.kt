package com.example.mediadownloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

class FFmpegEngineManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val elfHeader = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

    @Test
    fun isValidElf_detectsElfHeaderAccurately() {
        val elfFile = tempFolder.newFile("valid.so").apply {
            writeBytes(elfHeader + ByteArray(100) { 1 })
        }
        val textFile = tempFolder.newFile("invalid.so").apply {
            writeText("libavcodec.so.61.19.101\n")
        }
        val emptyFile = tempFolder.newFile("empty.so")

        assertTrue(FFmpegEngineManager.isValidElf(elfFile))
        assertFalse(FFmpegEngineManager.isValidElf(textFile))
        assertFalse(FFmpegEngineManager.isValidElf(emptyFile))
    }

    @Test
    fun isCorruptPlaceholder_identifiesShortTextPlaceholders() {
        val placeholder = tempFolder.newFile("placeholder.so").apply {
            writeText("target.so.1\n")
        }
        val realElf = tempFolder.newFile("real.so").apply {
            writeBytes(elfHeader + ByteArray(2048) { 2 })
        }

        assertTrue(FFmpegEngineManager.isCorruptPlaceholder(placeholder))
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(realElf))
    }

    @Test
    fun resolveSymlinkTarget_resolvesMultiHopChain() {
        val dir = tempFolder.newFolder("multihop")

        val realBinary = File(dir, "libglib-2.0.so.0.8600.0").apply {
            writeBytes(elfHeader + ByteArray(4096) { 0x42 })
        }
        val hop1 = File(dir, "libglib-2.0.so.0").apply {
            writeText("libglib-2.0.so.0.8600.0\n")
        }
        val hop0 = File(dir, "libglib-2.0.so").apply {
            writeText("libglib-2.0.so.0\n")
        }

        val resolved = FFmpegEngineManager.resolveSymlinkTarget(hop0, dir)
        assertNotNull(resolved)
        assertEquals(realBinary.canonicalPath, resolved?.canonicalPath)

        val resolvedHop1 = FFmpegEngineManager.resolveSymlinkTarget(hop1, dir)
        assertNotNull(resolvedHop1)
        assertEquals(realBinary.canonicalPath, resolvedHop1?.canonicalPath)
    }

    @Test
    fun resolveSymlinkTarget_detectsCyclesWithoutHanging() {
        val dir = tempFolder.newFolder("cycle")

        val fileA = File(dir, "liba.so").apply { writeText("libb.so\n") }
        val fileB = File(dir, "libb.so").apply { writeText("liba.so\n") }

        val resolved = FFmpegEngineManager.resolveSymlinkTarget(fileA, dir)
        assertNull(resolved)
    }

    @Test
    fun repairDirectory_repairsAllPlaceholdersInMultiHopChain() {
        val libDir = tempFolder.newFolder("usr", "lib")

        // Create genuine ELF targets
        val realGlib = File(libDir, "libglib-2.0.so.0.8600.0").apply {
            writeBytes(elfHeader + ByteArray(5000) { 0x11 })
        }
        val realCodec = File(libDir, "libavcodec.so.61.19.101").apply {
            writeBytes(elfHeader + ByteArray(8000) { 0x22 })
        }

        // Create single-hop placeholder
        val codecSymlink = File(libDir, "libavcodec.so.61").apply {
            writeText("libavcodec.so.61.19.101\n")
        }

        // Create multi-hop placeholders
        val glibHop1 = File(libDir, "libglib-2.0.so.0").apply {
            writeText("libglib-2.0.so.0.8600.0\n")
        }
        val glibHead = File(libDir, "libglib-2.0.so").apply {
            writeText("libglib-2.0.so.0\n")
        }

        assertTrue(FFmpegEngineManager.isCorruptPlaceholder(codecSymlink))
        assertTrue(FFmpegEngineManager.isCorruptPlaceholder(glibHop1))
        assertTrue(FFmpegEngineManager.isCorruptPlaceholder(glibHead))

        val repairedCount = FFmpegEngineManager.repairDirectory(libDir)
        assertTrue("Expected at least 3 repaired placeholders, got $repairedCount", repairedCount >= 3)

        // Verify that NO corrupt placeholders remain
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(codecSymlink))
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(glibHop1))
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(glibHead))

        // Verify all files have valid ELF contents
        assertTrue(FFmpegEngineManager.isValidElf(codecSymlink))
        assertTrue(FFmpegEngineManager.isValidElf(glibHop1))
        assertTrue(FFmpegEngineManager.isValidElf(glibHead))
    }

    @Test
    fun resolveSymlinkTarget_handlesDanglingPointerGracefully() {
        val dir = tempFolder.newFolder("dangling")
        val danglingPlaceholder = File(dir, "libdangling.so").apply {
            writeText("non_existent_target.so\n")
        }

        val resolved = FFmpegEngineManager.resolveSymlinkTarget(danglingPlaceholder, dir)
        assertNull(resolved)
    }

    @Test
    fun isCorruptPlaceholder_handlesZeroByteFiles() {
        val emptyFile = tempFolder.newFile("empty_zero_byte.so")
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(emptyFile))
        assertFalse(FFmpegEngineManager.isValidElf(emptyFile))
    }

    @Test
    fun repairDirectory_handlesCrossDirectoryReferences() {
        val rootDir = tempFolder.newFolder("cross_dir")
        val binDir = File(rootDir, "bin").apply { mkdirs() }
        val libDir = File(rootDir, "lib").apply { mkdirs() }

        val realBinary = File(libDir, "libffmpeg.so.61.1.0").apply {
            writeBytes(elfHeader + ByteArray(4000) { 0x55 })
        }
        val binPlaceholder = File(binDir, "ffmpeg").apply {
            writeText("../lib/libffmpeg.so.61.1.0\n")
        }

        assertTrue(FFmpegEngineManager.isCorruptPlaceholder(binPlaceholder))
        val repaired = FFmpegEngineManager.repairDirectory(rootDir)
        assertTrue(repaired >= 1)
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(binPlaceholder))
        assertTrue(FFmpegEngineManager.isValidElf(binPlaceholder))
    }

    @Test
    fun resolveSymlinkTarget_withLongTextContent_doesNotThrowFileNameTooLong() {
        val dir = tempFolder.newFolder("long_text")
        val longTextFile = File(dir, "readme.txt").apply {
            writeText("A".repeat(300))
        }
        val resolved = FFmpegEngineManager.resolveSymlinkTarget(longTextFile, dir)
        assertNull(resolved)
    }

    @Test
    fun isCorruptPlaceholder_ignoresDocumentationAndMultiLineFiles() {
        val dir = tempFolder.newFolder("text_files")
        val pcFile = File(dir, "libavcodec.pc").apply {
            writeText("prefix=/usr\nlibdir=\${prefix}/lib\nName: libavcodec\n")
        }
        val readme = File(dir, "README.txt").apply {
            writeText("This is ffmpeg build.\n")
        }
        val license = File(dir, "LICENSE").apply {
            writeText("GNU General Public License\nVersion 3\n")
        }

        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(pcFile))
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(readme))
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(license))
    }

    @Test
    fun repairDirectory_safelySkipsNonPlaceholderTextFilesWithoutCrashing() {
        val rootDir = tempFolder.newFolder("safe_skip")
        val libDir = File(rootDir, "lib").apply { mkdirs() }
        val pcFile = File(libDir, "libavcodec.pc").apply {
            writeText("prefix=/usr\nlibdir=\${prefix}/lib\nName: libavcodec\n" + "A".repeat(400))
        }
        val realBinary = File(libDir, "libffmpeg.so.61.1.0").apply {
            writeBytes(elfHeader + ByteArray(4000) { 0x55 })
        }
        val validPlaceholder = File(libDir, "libffmpeg.so.61").apply {
            writeText("libffmpeg.so.61.1.0\n")
        }

        val repaired = FFmpegEngineManager.repairDirectory(rootDir)
        assertEquals(1, repaired)
        assertTrue(pcFile.exists())
        assertTrue(FFmpegEngineManager.isValidElf(validPlaceholder))
    }

    @Test
    fun isCorruptPlaceholder_ignoresLibOpenClLaAndNonLibraryFiles() {
        val dir = tempFolder.newFolder("la_files")
        val laFile = File(dir, "libOpenCL.la").apply {
            writeText("# libOpenCL.la - a libtool library file\n# Generated by libtool (GNU libtool) 2.4.6\n")
        }
        val staticLib = File(dir, "libfoo.a").apply {
            writeText("!<arch>\n")
        }
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(laFile))
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(staticLib))
    }

    @Test
    fun isCorruptPlaceholder_rejectsContentWithNullCharacters() {
        val dir = tempFolder.newFolder("null_chars")
        val nullFile = File(dir, "libavcodec.so.1").apply {
            writeBytes(byteArrayOf('l'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(), 0, 'a'.code.toByte()))
        }
        assertFalse(FFmpegEngineManager.isCorruptPlaceholder(nullFile))
    }

    @Test
    fun repairDirectory_handlesTargetCanonicalEqualToSourceSafely() {
        val rootDir = tempFolder.newFolder("self_ref")
        val libDir = File(rootDir, "lib").apply { mkdirs() }
        val selfFile = File(libDir, "libself.so").apply {
            writeText("libself.so\n")
        }
        val repaired = FFmpegEngineManager.repairDirectory(rootDir)
        assertEquals(0, repaired)
        assertTrue(selfFile.exists())
    }

    @Test
    fun getFFmpegLocation_prefersNativeLibraryDirWhenPresent() {
        val nativeDir = tempFolder.newFolder("native_lib")
        val fakeFfmpeg = File(nativeDir, "libffmpeg.so").apply {
            writeBytes(elfHeader + ByteArray(100))
        }
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = nativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
        }

        val location = FFmpegEngineManager.getFFmpegLocation(mockContext)
        assertEquals(fakeFfmpeg.absolutePath, location)
    }

    @Test
    fun getFFmpegLocation_fallsBackWhenNativeLibraryDirMissing() {
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = File(tempFolder.root, "non_existent_native").absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
        }

        // Create packages/ffmpeg structure inside noBackupFilesDir
        val ytBase = File(tempFolder.root, "youtubedl-android")
        val packagesDir = File(ytBase, "packages")
        val ffmpegDir = File(packagesDir, "ffmpeg").apply { mkdirs() }
        val ffmpegBin = File(ffmpegDir, "bin").apply { mkdirs() }
        File(ffmpegBin, "ffmpeg").apply { writeBytes(elfHeader + ByteArray(50)) }
        File(ffmpegBin, "ffprobe").apply { writeBytes(elfHeader + ByteArray(50)) }

        val location = FFmpegEngineManager.getFFmpegLocation(mockContext)
        assertEquals(ffmpegBin.absolutePath, location)
    }

    @Test
    fun isInstalled_returnsFalse_whenPackagesDirDoesNotExist() {
        val nativeDir = tempFolder.newFolder("native_clean_install")
        File(nativeDir, "libffmpeg.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        File(nativeDir, "libffprobe.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = nativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
        }

        // Fresh install: native libffmpeg exists in APK, but packages/ffmpeg has not been downloaded yet
        assertFalse(FFmpegEngineManager.isInstalled(mockContext))
    }

    @Test
    fun isInstalled_returnsFalse_whenKeyLibrariesMissing() {
        val nativeDir = tempFolder.newFolder("native_missing_keys")
        File(nativeDir, "libffmpeg.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        File(nativeDir, "libffprobe.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = nativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
        }

        val ytBase = File(tempFolder.root, "youtubedl-android")
        val packagesDir = File(ytBase, "packages")
        val ffmpegDir = File(packagesDir, "ffmpeg").apply { mkdirs() }
        val libDir = File(ffmpegDir, "usr/lib").apply { mkdirs() }
        // Only provide libavcodec, missing other required libraries
        File(libDir, "libavcodec.so.61.1.0").apply { writeBytes(elfHeader + ByteArray(100000)) }

        assertFalse(FFmpegEngineManager.isInstalled(mockContext))
    }

    @Test
    fun isInstalled_returnsFalse_whenCorruptPlaceholderPresent() {
        val nativeDir = tempFolder.newFolder("native_corrupt_placeholder")
        File(nativeDir, "libffmpeg.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        File(nativeDir, "libffprobe.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = nativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
        }

        val ytBase = File(tempFolder.root, "youtubedl-android")
        val packagesDir = File(ytBase, "packages")
        val ffmpegDir = File(packagesDir, "ffmpeg").apply { mkdirs() }
        val libDir = File(ffmpegDir, "usr/lib").apply { mkdirs() }
        val keys = listOf(
            "libavcodec", "libavformat", "libavutil", "libswscale",
            "libswresample", "libavfilter", "libavdevice", "libpostproc"
        )
        for (k in keys) {
            File(libDir, "${k}.so.1.0").apply { writeBytes(elfHeader + ByteArray(100000)) }
        }
        // Add an unresolved corrupt placeholder for libavcodec.so.61
        File(libDir, "libavcodec.so.61").apply { writeText("libavcodec.so.61.1.0\n") }

        assertFalse(FFmpegEngineManager.isInstalled(mockContext))
    }

    @Test
    fun isInstalled_returnsTrue_whenNativeExecutablesAndRepairedLibDirPresent() {
        val nativeDir = tempFolder.newFolder("native_valid_install")
        File(nativeDir, "libffmpeg.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        File(nativeDir, "libffprobe.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = nativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
        }

        val ytBase = File(tempFolder.root, "youtubedl-android")
        val packagesDir = File(ytBase, "packages")
        val ffmpegDir = File(packagesDir, "ffmpeg").apply { mkdirs() }
        val libDir = File(ffmpegDir, "usr/lib").apply { mkdirs() }
        val keys = listOf(
            "libavcodec", "libavformat", "libavutil", "libswscale",
            "libswresample", "libavfilter", "libavdevice", "libpostproc"
        )
        for (k in keys) {
            File(libDir, "${k}.so.1.0").apply { writeBytes(elfHeader + ByteArray(100000)) }
        }

        assertTrue(FFmpegEngineManager.isInstalled(mockContext))
    }
}
