package com.example.mediadownloader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadConfigurationTest {

    @Test
    fun defaultConfiguration_isBestQualityMp4Video() {
        val config = DownloadConfiguration()
        assertEquals(MediaType.VIDEO, config.mediaType)
        assertEquals(VideoFormat.MP4, config.videoFormat)
        assertEquals(VideoQuality.BEST, config.videoQuality)
        assertEquals("mp4", config.extension)
        assertEquals("video/mp4", config.mimeType)
        assertEquals("MP4", config.formatDisplayName)
        assertNull(config.videoQuality.maxHeight)
        assertEquals("bestvideo+bestaudio/best", config.videoQuality.ytdlFormatSelector)
    }

    @Test
    fun videoQualities_haveCorrectYtdlFormatSelectors() {
        assertEquals("bestvideo+bestaudio/best", VideoQuality.BEST.ytdlFormatSelector)
        assertEquals("bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", VideoQuality.P1080.ytdlFormatSelector)
        assertEquals("bestvideo[height<=720]+bestaudio/best[height<=720]/best", VideoQuality.P720.ytdlFormatSelector)
        assertEquals("bestvideo[height<=480]+bestaudio/best[height<=480]/best", VideoQuality.P480.ytdlFormatSelector)
    }

    @Test
    fun audioQualities_haveCorrectYtdlQualityValues() {
        assertEquals("0", AudioQuality.BEST.ytdlQuality)
        assertEquals("2", AudioQuality.HIGH.ytdlQuality)
        assertEquals("4", AudioQuality.MEDIUM.ytdlQuality)
        assertEquals("6", AudioQuality.LOW.ytdlQuality)
    }

    @Test
    fun audioConfiguration_mimeTypeAndExtensionsMatch() {
        val flacConfig = DownloadConfiguration(
            mediaType = MediaType.AUDIO,
            audioFormat = AudioFormat.FLAC,
            audioQuality = AudioQuality.BEST
        )
        assertEquals("flac", flacConfig.extension)
        assertEquals("audio/flac", flacConfig.mimeType)
        assertEquals("FLAC", flacConfig.formatDisplayName)
        assertEquals(DownloadFormat.FLAC, flacConfig.asDownloadFormat)

        val opusConfig = DownloadConfiguration(
            mediaType = MediaType.AUDIO,
            audioFormat = AudioFormat.OPUS,
            audioQuality = AudioQuality.BEST
        )
        assertEquals("opus", opusConfig.extension)
        assertEquals("audio/opus", opusConfig.mimeType)
        assertEquals(DownloadFormat.OPUS, opusConfig.asDownloadFormat)
    }

    @Test
    fun fromDownloadFormat_mapsCorrectly() {
        val mkvConfig = DownloadConfiguration.fromDownloadFormat(DownloadFormat.MKV)
        assertEquals(MediaType.VIDEO, mkvConfig.mediaType)
        assertEquals(VideoFormat.MKV, mkvConfig.videoFormat)

        val mp3Config = DownloadConfiguration.fromDownloadFormat(DownloadFormat.MP3)
        assertEquals(MediaType.AUDIO, mp3Config.mediaType)
        assertEquals(AudioFormat.MP3, mp3Config.audioFormat)
    }
}
