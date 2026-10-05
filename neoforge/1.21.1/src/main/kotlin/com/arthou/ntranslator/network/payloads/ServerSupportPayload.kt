package com.arthou.ntranslator.network.payloads

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data object ServerSupportPayload : CustomPacketPayload {
    val CODEC: StreamCodec<RegistryFriendlyByteBuf, ServerSupportPayload> = StreamCodec.of(
        { _, _ -> },
        { ServerSupportPayload }
    )

    val EMPTY: ServerSupportPayload = ServerSupportPayload

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.SERVER_SUPPORT
    }
}
