package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf
import java.util.UUID

data class SendChatBubblePayload(
    val sourceId: UUID,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val index: Int,
    val updateTime: Long,
    val bubbleAppearance: SpeechBubbleAppearance,
    val bubbleVoice: BubbleVoice,
    val text: String
) : LegacyPayload {
    override val id = PacketIds.SEND_CHAT_BUBBLE

    override fun write(buf: FriendlyByteBuf) {
        buf.writeUUID(sourceId)
        buf.writeEnum(sourceLanguage)
        buf.writeEnum(targetLanguage)
        buf.writeVarInt(index)
        buf.writeVarLong(updateTime)
        bubbleAppearance.write(buf)
        bubbleVoice.write(buf)
        buf.writeUtf(text)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): SendChatBubblePayload = SendChatBubblePayload(
            sourceId = buf.readUUID(),
            sourceLanguage = buf.readEnum(Language::class.java),
            targetLanguage = buf.readEnum(Language::class.java),
            index = buf.readVarInt(),
            updateTime = buf.readVarLong(),
            bubbleAppearance = SpeechBubbleAppearance.read(buf),
            bubbleVoice = BubbleVoice.read(buf),
            text = buf.readUtf()
        )
    }
}
