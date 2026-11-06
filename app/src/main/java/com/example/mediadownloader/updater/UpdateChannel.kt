package com.example.mediadownloader.updater

enum class UpdateChannel(val channelKey: String) {
    STABLE("stable"),
    ALPHA("alpha");

    companion object {
        fun fromKey(key: String?): UpdateChannel {
            return when (key?.lowercase()?.trim()) {
                "alpha" -> ALPHA
                else -> STABLE
            }
        }
    }
}
