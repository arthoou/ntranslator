package com.arthou.ntranslator.config

enum class SpeechBubbleLineMode {
    STACKED,
    SINGLE_LINE,
    @Deprecated("Legacy config alias. Use STACKED instead.")
    QSMP1,
    @Deprecated("Legacy config alias. Use SINGLE_LINE instead.")
    QSMP2;

    fun resolved(): SpeechBubbleLineMode {
        return when (this) {
            QSMP1 -> STACKED
            QSMP2 -> SINGLE_LINE
            else -> this
        }
    }
}
