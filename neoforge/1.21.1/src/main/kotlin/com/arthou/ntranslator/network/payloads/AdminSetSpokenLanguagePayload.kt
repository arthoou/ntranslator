package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class AdminSetSpokenLanguagePayload(val language: Language) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AdminSetSpokenLanguagePayload> = StreamCodec.of(
            { buf, payload -> buf.writeEnum(payload.language) },
            { buf -> AdminSetSpokenLanguagePayload(buf.readEnum(Language::class.java)) }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.ADMIN_SET_SPOKEN_LANGUAGE
    }
}
