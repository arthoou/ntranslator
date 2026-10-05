package com.arthou.ntranslator.config

enum class SpeechBubbleStyle(val textureKey: String) {
    SQUARED("squared"),
    ROUNDED("rounded"),
    @Deprecated("Legacy only, rendered as rounded.")
    CIRCULAR("rounded");

    fun resolved(): SpeechBubbleStyle {
        return if (this == CIRCULAR) ROUNDED else this
    }
}
