package com.arthou.ntranslator.config

data class SpeechBubbleConfig(
    var enabled: Boolean = true,
    var showOwnBubble: Boolean = true,
    var style: SpeechBubbleStyle = SpeechBubbleStyle.ROUNDED,
    var lineMode: SpeechBubbleLineMode = SpeechBubbleLineMode.STACKED,
    var font: SpeechBubbleFont = SpeechBubbleFont.NEXEL,
    var textColor: Int = 0x000000,
    var fillColor: Int = 0xFFFFFF,
    var borderColor: Int = 0x000000,

    @get:IntRange(from = 96, to = 320, increment = 8)
    var maxWidth: Int = 180,

    @get:IntRange(from = 0, to = 8, increment = 1)
    var padding: Int = 2,

    @get:FloatRange(from = 0.0f, to = 2.5f, increment = 0.05f)
    var heightOffset: Float = 0.9f,

    @get:FloatRange(from = 1.0f, to = 30.0f, increment = 0.5f)
    var displaySeconds: Float = 8.0f,

    @get:FloatRange(from = 0.1f, to = 5.0f, increment = 0.1f)
    var fadeSeconds: Float = 0.6f
)
