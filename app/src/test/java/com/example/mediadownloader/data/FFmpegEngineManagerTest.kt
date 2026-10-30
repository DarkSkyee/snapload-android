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

    @Test
    fun unzip_safelyOverwritesExistingFilesWithoutFailing() {
        val destDir = tempFolder.newFolder("unzip_dest")
        val existingFile = File(destDir, "test.txt").apply {
            writeText("old content")
        }

        // Create a test zip archive with updated test.txt
        val zipFile = tempFolder.newFile("test.zip")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("test.txt"))
            zos.write("new content".toByteArray())
            zos.closeEntry()
        }

        FFmpegEngineManager.unzip(zipFile, destDir)
        assertEquals("new content", existingFile.readText())
    }

    @Test
    fun ensureFFmpegInstalled_extractsBundledArchiveFromNativeLibraryDir() = kotlinx.coroutines.runBlocking {
        val nativeDir = tempFolder.newFolder("native_bundled_test")
        File(nativeDir, "libffmpeg.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        File(nativeDir, "libffprobe.so").apply { writeBytes(elfHeader + ByteArray(100)) }

        // Create bundled libffmpeg.zip.so with required media libraries
        val bundledZip = File(nativeDir, "libffmpeg.zip.so")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(bundledZip)).use { zos ->
            val keys = listOf(
                "libavcodec", "libavformat", "libavutil", "libswscale",
                "libswresample", "libavfilter", "libavdevice", "libpostproc"
            )
            for (k in keys) {
                zos.putNextEntry(java.util.zip.ZipEntry("usr/lib/${k}.so.1.0"))
                zos.write(elfHeader + ByteArray(20000))
                zos.closeEntry()
            }
            // Pad zip to exceed 1MB threshold
            zos.putNextEntry(java.util.zip.ZipEntry("usr/lib/padding.bin"))
            zos.write(ByteArray(1024 * 1024 + 10))
            zos.closeEntry()
        }

        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = nativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
            override fun getCacheDir(): File = tempFolder.root
        }

        val success = FFmpegEngineManager.ensureFFmpegInstalled(mockContext)
        assertTrue("Expected ensureFFmpegInstalled to succeed with bundled archive", success)
        assertTrue("Expected isInstalled to return true after extracting bundled archive", FFmpegEngineManager.isInstalled(mockContext))
    }

    @Test
    fun repairSymlinksAndBinaries_overwritesBrokenSymlinksInBinDir() {
        val nativeDir = tempFolder.newFolder("native_broken_symlink_test")
        val realFfmpeg = File(nativeDir, "libffmpeg.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        val realFfprobe = File(nativeDir, "libffprobe.so").apply { writeBytes(elfHeader + ByteArray(100)) }

        val ytBase = File(tempFolder.root, "youtubedl-android")
        val packagesDir = File(ytBase, "packages")
        val ffmpegDir = File(packagesDir, "ffmpeg").apply { mkdirs() }
        val binDir = File(ffmpegDir, "bin").apply { mkdirs() }

        // Create broken symlink in binDir pointing to a dead file
        val deadTarget = File(tempFolder.root, "dead_target.so")
        val binFfmpeg = File(binDir, "ffmpeg")
        var symlinkCreated = false
        try {
            java.nio.file.Files.createSymbolicLink(binFfmpeg.toPath(), deadTarget.toPath())
            symlinkCreated = true
        } catch (_: Throwable) {}

        if (symlinkCreated) {
            // Confirm it is indeed a broken symlink
            assertFalse(binFfmpeg.exists())
            assertTrue(java.nio.file.Files.isSymbolicLink(binFfmpeg.toPath()))

            val appInfo = ApplicationInfo().apply {
                nativeLibraryDir = nativeDir.absolutePath
            }
            val mockContext = object : android.content.ContextWrapper(null) {
                override fun getApplicationInfo(): ApplicationInfo = appInfo
                override fun getApplicationContext(): Context = this
                override fun getNoBackupFilesDir(): File = tempFolder.root
            }

            FFmpegEngineManager.repairSymlinksAndBinaries(mockContext)
            assertTrue("ffmpeg in binDir must exist and not be broken after repair", binFfmpeg.exists())
            assertEquals(realFfmpeg.length(), binFfmpeg.length())
        }
    }

    @Test
    fun ensureFFmpegInstalled_fallsBackToApkSourceDir_whenNativeLibraryDirMissingBundle() = kotlinx.coroutines.runBlocking {
        val apkDir = tempFolder.newFolder("apk_dir")
        val fakeApk = File(apkDir, "base.apk")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(fakeApk)).use { zos ->
            // Package lib/arm64-v8a/libffmpeg.zip.so inside fake APK
            zos.putNextEntry(java.util.zip.ZipEntry("lib/arm64-v8a/libffmpeg.zip.so"))
            val innerZipBytes = java.io.ByteArrayOutputStream().also { innerBos ->
                java.util.zip.ZipOutputStream(innerBos).use { innerZos ->
                    val keys = listOf(
                        "libavcodec", "libavformat", "libavutil", "libswscale",
                        "libswresample", "libavfilter", "libavdevice", "libpostproc"
                    )
                    for (k in keys) {
                        innerZos.putNextEntry(java.util.zip.ZipEntry("usr/lib/${k}.so.1.0"))
                        innerZos.write(elfHeader + ByteArray(20000))
                        innerZos.closeEntry()
                    }
                }
            }.toByteArray()
            zos.write(innerZipBytes)
            zos.closeEntry()

            // Package lib/arm64-v8a/libffmpeg.so inside fake APK
            zos.putNextEntry(java.util.zip.ZipEntry("lib/arm64-v8a/libffmpeg.so"))
            zos.write(elfHeader + ByteArray(100))
            zos.closeEntry()

            // Package lib/arm64-v8a/libffprobe.so inside fake APK
            zos.putNextEntry(java.util.zip.ZipEntry("lib/arm64-v8a/libffprobe.so"))
            zos.write(elfHeader + ByteArray(100))
            zos.closeEntry()
        }

        // nativeLibraryDir does NOT have libffmpeg.zip.so (simulating devices where OS didn't extract zip.so)
        val emptyNativeDir = tempFolder.newFolder("empty_native_dir")
        val appInfo = ApplicationInfo().apply {
            sourceDir = fakeApk.absolutePath
            nativeLibraryDir = emptyNativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
            override fun getCacheDir(): File = tempFolder.root
        }

        val success = FFmpegEngineManager.ensureFFmpegInstalled(mockContext)
        assertTrue("Expected ensureFFmpegInstalled to extract from APK sourceDir", success)
        assertTrue(FFmpegEngineManager.isInstalled(mockContext))
    }

    @Test
    fun ensureFFmpegInstalled_recoversFromCorruptedPreviousInstall() = kotlinx.coroutines.runBlocking {
        val nativeDir = tempFolder.newFolder("native_corrupt_recovery")
        File(nativeDir, "libffmpeg.so").apply { writeBytes(elfHeader + ByteArray(100)) }
        File(nativeDir, "libffprobe.so").apply { writeBytes(elfHeader + ByteArray(100)) }

        // Setup corrupted packages/ffmpeg directory simulating an interrupted download/extraction
        val ytBase = File(tempFolder.root, "youtubedl-android")
        val packagesDir = File(ytBase, "packages")
        val ffmpegDir = File(packagesDir, "ffmpeg").apply { mkdirs() }
        val libDir = File(ffmpegDir, "usr/lib").apply { mkdirs() }
        File(libDir, "libavcodec.so.61").apply { writeText("non_existent_target.so\n") }
        File(libDir, "corrupted_garbage.bin").apply { writeBytes(ByteArray(500)) }

        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = nativeDir.absolutePath
        }
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = tempFolder.root
            override fun getCacheDir(): File = tempFolder.root
        }

        // Must initially fail isInstalled check due to corrupt placeholder
        assertFalse(FFmpegEngineManager.isInstalled(mockContext))

        // Create bundled zip with valid media libraries
        val bundledZip = File(nativeDir, "libffmpeg.zip.so")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(bundledZip)).use { zos ->
            val keys = listOf(
                "libavcodec", "libavformat", "libavutil", "libswscale",
                "libswresample", "libavfilter", "libavdevice", "libpostproc"
            )
            for (k in keys) {
                zos.putNextEntry(java.util.zip.ZipEntry("usr/lib/${k}.so.1.0"))
                zos.write(elfHeader + ByteArray(20000))
                zos.closeEntry()
            }
            zos.putNextEntry(java.util.zip.ZipEntry("usr/lib/padding.bin"))
            zos.write(ByteArray(1024 * 1024 + 10))
            zos.closeEntry()
        }

        val success = FFmpegEngineManager.ensureFFmpegInstalled(mockContext)
        assertTrue("Expected ensureFFmpegInstalled to recover and succeed", success)
        assertTrue(FFmpegEngineManager.isInstalled(mockContext))
        assertFalse("Corrupted leftover garbage must be wiped during clean recovery", File(libDir, "corrupted_garbage.bin").exists())
    }

    @Test
    fun setupBinaryAlias_replacesStaleOrCorruptDestination() {
        val testDir = tempFolder.newFolder("alias_test")
        val sourceFile = File(testDir, "source.so").apply {
            writeBytes(elfHeader + ByteArray(500))
        }
        val destFile = File(testDir, "alias.so").apply {
            writeBytes(ByteArray(10)) // stale, wrong size
        }

        FFmpegEngineManager.setupBinaryAlias(sourceFile, destFile)
        assertTrue(destFile.exists())
        assertEquals(sourceFile.length(), destFile.length())
    }

    @Test
    fun unzip_handlesInputStreamDirectly() {
        val destDir = tempFolder.newFolder("stream_dest")
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("streamed.txt"))
            zos.write("hello stream".toByteArray())
            zos.closeEntry()
        }

        FFmpegEngineManager.unzip(java.io.ByteArrayInputStream(bos.toByteArray()), destDir)
        val extracted = File(destDir, "streamed.txt")
        assertTrue(extracted.exists())
        assertEquals("hello stream", extracted.readText())
    }
}
