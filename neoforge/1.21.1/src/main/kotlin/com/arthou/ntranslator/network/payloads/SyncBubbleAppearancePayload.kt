package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.config.SpeechBubbleAppearance
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class SyncBubbleAppearancePayload(val appearance: SpeechBubbleAppearance) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncBubbleAppearancePayload> = StreamCodec.of(
            { buf, payload -> payload.appearance.write(buf) },
            { buf -> SyncBubbleAppearancePayload(SpeechBubbleAppearance.read(buf)) }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.SYNC_BUBBLE_APPEARANCE
    }
}
