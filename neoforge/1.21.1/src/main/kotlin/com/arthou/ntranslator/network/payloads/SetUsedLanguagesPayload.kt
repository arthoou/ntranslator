package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class SetUsedLanguagesPayload(val languages: List<Language>) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SetUsedLanguagesPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeVarInt(payload.languages.size)
                payload.languages.forEach(buf::writeEnum)
            },
            { buf ->
                val size = buf.readVarInt()
                val languages = ArrayList<Language>(size)
                repeat(size) {
                    languages.add(buf.readEnum(Language::class.java))
                }
                SetUsedLanguagesPayload(languages)
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.SET_USED_LANGUAGES
    }
}
