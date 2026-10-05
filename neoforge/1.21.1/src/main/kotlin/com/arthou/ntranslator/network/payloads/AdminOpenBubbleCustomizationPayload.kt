package com.arthou.ntranslator.network.payloads

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

class AdminOpenBubbleCustomizationPayload : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AdminOpenBubbleCustomizationPayload> = StreamCodec.unit(
            AdminOpenBubbleCustomizationPayload()
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.ADMIN_OPEN_BUBBLE_CUSTOMIZATION
    }
}
