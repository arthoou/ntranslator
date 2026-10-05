package com.arthou.ntranslator.config

enum class SpeechBubbleFont {
    @Deprecated("Legacy hidden option. Use NEXEL instead.")
    AUTO,

    MINECRAFT,
    NEXEL,
    VANILLA_TWEAKS,
    VANILLA_TWEAKS_ALT,

    @Deprecated("Legacy hidden option. Use NEXEL instead.")
    GALACTIC,

    @Deprecated("Legacy hidden option. Use NEXEL instead.")
    TOONISH,

    @Deprecated("Legacy hidden option. Use NEXEL instead.")
    ILLAGER,

    @Deprecated("Legacy hidden option. Use NEXEL instead.")
    SUPREME;

    fun resolved(): SpeechBubbleFont {
        return when (this) {
            AUTO,
            GALACTIC,
            TOONISH,
            ILLAGER,
            SUPREME -> NEXEL
            else -> this
        }
    }

    companion object {
        val visible: List<SpeechBubbleFont> = listOf(
            MINECRAFT,
            NEXEL,
            VANILLA_TWEAKS_ALT
        )
    }
}
