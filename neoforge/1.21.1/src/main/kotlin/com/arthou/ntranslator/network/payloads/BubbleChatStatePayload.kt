package com.arthou.ntranslator.network.payloads

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class BubbleChatStatePayload(
    val enabled: Boolean
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.BUBBLE_CHAT_STATE
    }

    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BubbleChatStatePayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, BubbleChatStatePayload::enabled,
            ::BubbleChatStatePayload
        )
    }
}
