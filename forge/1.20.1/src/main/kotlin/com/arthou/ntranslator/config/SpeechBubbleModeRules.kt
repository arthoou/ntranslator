package com.arthou.ntranslator.config

object SpeechBubbleModeRules {
    fun forceSingleLineForGooglePlaceholder(serverCanHandleRealtimeTranslation: Boolean = false): Boolean {
        return false
    }

    fun appearanceForCurrentTranslationMode(
        appearance: SpeechBubbleAppearance,
        serverCanHandleRealtimeTranslation: Boolean = false
    ): SpeechBubbleAppearance {
        return appearance
    }

    fun effectiveLineMode(config: SpeechBubbleConfig, serverCanHandleRealtimeTranslation: Boolean = false): SpeechBubbleLineMode {
        return config.lineMode.resolved()
    }
}
