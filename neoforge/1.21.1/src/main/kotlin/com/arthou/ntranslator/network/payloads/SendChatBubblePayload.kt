package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
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
) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SendChatBubblePayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeUUID(payload.sourceId)
                buf.writeEnum(payload.sourceLanguage)
                buf.writeEnum(payload.targetLanguage)
                buf.writeVarInt(payload.index)
                buf.writeVarLong(payload.updateTime)
                payload.bubbleAppearance.write(buf)
                payload.bubbleVoice.write(buf)
                buf.writeUtf(payload.text)
            },
            { buf ->
                SendChatBubblePayload(
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
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.SEND_CHAT_BUBBLE
    }
}
