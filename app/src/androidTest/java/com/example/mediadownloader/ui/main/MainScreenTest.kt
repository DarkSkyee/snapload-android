package com.example.mediadownloader.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.mediadownloader.model.DownloadFormat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.mediadownloader.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      FormatSelectorCard(
        selectedFormat = DownloadFormat.MP4,
        onFormatSelected = {}
      )
    }
  }

  @Test
  fun formatSelector_displaysOptions() {
    composeTestRule.onNodeWithText("MP4 Wideo HD").assertExists()
    composeTestRule.onNodeWithText("MP3 Dźwięk").assertExists()
  }
}
