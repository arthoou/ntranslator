package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf
import java.util.LinkedHashMap
import java.util.UUID

data class SendTranscriptToClientPayload(
    val uuid: UUID,
    val language: Language,
    val index: Int,
    val updateTime: Long,
    val bubbleAppearance: SpeechBubbleAppearance,
    val bubbleVoice: BubbleVoice,
    val toSend: Map<Language, String>
) : LegacyPayload {
    override val id = PacketIds.SEND_TRANSCRIPT_TO_CLIENT

    override fun write(buf: FriendlyByteBuf) {
        buf.writeUUID(uuid)
        buf.writeEnum(language)
        buf.writeVarInt(index)
        buf.writeVarLong(updateTime)
        bubbleAppearance.write(buf)
        bubbleVoice.write(buf)
        buf.writeVarInt(toSend.size)
        toSend.forEach { (language, text) ->
            buf.writeEnum(language)
            buf.writeUtf(text)
        }
    }

    companion object {
        fun read(buf: FriendlyByteBuf): SendTranscriptToClientPayload {
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
            return SendTranscriptToClientPayload(sourceId, sourceLanguage, index, updateTime, bubbleAppearance, bubbleVoice, translations)
        }
    }
}
