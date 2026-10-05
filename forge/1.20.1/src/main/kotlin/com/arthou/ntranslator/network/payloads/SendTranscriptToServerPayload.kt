package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf

data class SendTranscriptToServerPayload(
    val sourceLanguage: Language,
    val text: String,
    val index: Int,
    val updateTime: Long,
    val bubbleAppearance: SpeechBubbleAppearance,
    val isFinal: Boolean
) : LegacyPayload {
    override val id = PacketIds.SEND_TRANSCRIPT_TO_SERVER

    override fun write(buf: FriendlyByteBuf) {
        buf.writeEnum(sourceLanguage)
        buf.writeUtf(text)
        buf.writeVarInt(index)
        buf.writeVarLong(updateTime)
        bubbleAppearance.write(buf)
        buf.writeBoolean(isFinal)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): SendTranscriptToServerPayload = SendTranscriptToServerPayload(
            sourceLanguage = buf.readEnum(Language::class.java),
            text = buf.readUtf(),
            index = buf.readVarInt(),
            updateTime = buf.readVarLong(),
            bubbleAppearance = SpeechBubbleAppearance.read(buf),
            isFinal = buf.readBoolean()
        )
    }
}
