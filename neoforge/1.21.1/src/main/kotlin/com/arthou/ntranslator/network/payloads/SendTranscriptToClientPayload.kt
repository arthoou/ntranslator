package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.LinkedHashMap
import java.util.UUID

data class SendTranscriptToClientPayload(
    val uuid: UUID,
    val language: Language,
    val index: Int,
    val updateTime: Long,
    val bubbleAppearance: SpeechBubbleAppearance,
    val bubbleVoice: BubbleVoice,
    val toSend: Map<Language, String>,
    val ignoreTranscriptRange: Boolean = false
) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SendTranscriptToClientPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeUUID(payload.uuid)
                buf.writeEnum(payload.language)
                buf.writeVarInt(payload.index)
                buf.writeVarLong(payload.updateTime)
                payload.bubbleAppearance.write(buf)
                payload.bubbleVoice.write(buf)
                buf.writeVarInt(payload.toSend.size)
                payload.toSend.forEach { (language, text) ->
                    buf.writeEnum(language)
                    buf.writeUtf(text)
                }
                buf.writeBoolean(payload.ignoreTranscriptRange)
            },
            { buf ->
                val sourceId = buf.readUUID()
                val sourceLanguage = buf.readEnum(Language::class.java)
                val index = buf.readVarInt()
                val updateTime = buf.readVarLong()
                val bubbleAppearance = SpeechBubbleAppearance.read(buf)
                val bubbleVoice = BubbleVoice.read(buf)
                val size = buf.readVarInt()
                val translations = LinkedHashMap<Language, String>(size)
                repeat(size) {
                    translations[buf.readEnum(Language::class.java)] = buf.readUtf()
                }
                val ignoreTranscriptRange = buf.readBoolean()
                SendTranscriptToClientPayload(sourceId, sourceLanguage, index, updateTime, bubbleAppearance, bubbleVoice, translations, ignoreTranscriptRange)
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.SEND_TRANSCRIPT_TO_CLIENT
    }
}
