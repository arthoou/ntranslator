package com.arthou.ntranslator.network.payloads

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class AdminOpenBrowserPayload(
    val hidden: Boolean,
    val browser: String
) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AdminOpenBrowserPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeBoolean(payload.hidden)
                buf.writeUtf(payload.browser)
            },
            { buf ->
                AdminOpenBrowserPayload(
                    hidden = buf.readBoolean(),
                    browser = buf.readUtf()
                )
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.ADMIN_OPEN_BROWSER
    }
}
