package com.arthou.ntranslator.config

import net.minecraft.network.FriendlyByteBuf

enum class BubbleVoice(
    val commandName: String,
    val browserId: String
) {
    OFF("off", "off"),
    ITALIAN("italian", "voice_1"),
    POLISH("polish", "voice_2"),
    ENGLISH("english", "voice_3"),
    BRAZIL("brazil", "voice_4"),
    SPANISH("spanish", "voice_5");

    companion object {
        val selectable = listOf(ITALIAN, POLISH, ENGLISH, BRAZIL, SPANISH, OFF)

        fun fromCommand(value: String): BubbleVoice? {
            val normalized = value.trim().lowercase()
            return when (normalized) {
                "off", "disable", "disabled", "none", "0" -> OFF
                "brazil", "brasil", "portuguese", "portugues" -> BRAZIL
                else -> selectable.firstOrNull {
                    it.commandName == normalized ||
                        it.name.lowercase() == normalized
                }
            }
        }

        fun read(buf: FriendlyByteBuf): BubbleVoice {
            return buf.readEnum(BubbleVoice::class.java)
        }
    }

    fun write(buf: FriendlyByteBuf) {
        buf.writeEnum(this)
    }
}
