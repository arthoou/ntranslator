package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class SendTranscriptToServerPayload(
    val sourceLanguage: Language,
    val text: String,
    val index: Int,
    val updateTime: Long,
    val bubbleAppearance: SpeechBubbleAppearance,
    val isFinal: Boolean
) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SendTranscriptToServerPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeEnum(payload.sourceLanguage)
                buf.writeUtf(payload.text)
                buf.writeVarInt(payload.index)
                buf.writeVarLong(payload.updateTime)
                payload.bubbleAppearance.write(buf)
                buf.writeBoolean(payload.isFinal)
            },
            { buf ->
                SendTranscriptToServerPayload(
                    sourceLanguage = buf.readEnum(Language::class.java),
                    text = buf.readUtf(),
                    index = buf.readVarInt(),
                    updateTime = buf.readVarLong(),
                    bubbleAppearance = SpeechBubbleAppearance.read(buf),
                    isFinal = buf.readBoolean()
                )
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.SEND_TRANSCRIPT_TO_SERVER
    }
}
