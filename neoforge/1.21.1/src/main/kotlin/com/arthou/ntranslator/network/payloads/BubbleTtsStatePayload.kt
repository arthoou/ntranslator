package com.arthou.ntranslator.network.payloads

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class BubbleTtsStatePayload(
    val enabled: Boolean
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.BUBBLE_TTS_STATE
    }

    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BubbleTtsStatePayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, BubbleTtsStatePayload::enabled,
            ::BubbleTtsStatePayload
        )
    }
}
