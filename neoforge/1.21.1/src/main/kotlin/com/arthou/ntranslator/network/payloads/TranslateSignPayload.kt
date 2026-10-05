package com.arthou.ntranslator.network.payloads

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class TranslateSignPayload(val pos: BlockPos) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TranslateSignPayload> = StreamCodec.of(
            { buf, payload -> buf.writeBlockPos(payload.pos) },
            { buf -> TranslateSignPayload(buf.readBlockPos()) }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.TRANSLATE_SIGN
    }
}
