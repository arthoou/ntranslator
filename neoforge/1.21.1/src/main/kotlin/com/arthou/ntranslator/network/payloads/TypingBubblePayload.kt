package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.config.SpeechBubbleAppearance
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

data class TypingBubblePayload(
    val sourceId: UUID,
    val active: Boolean,
    val appearance: SpeechBubbleAppearance
) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TypingBubblePayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeUUID(payload.sourceId)
                buf.writeBoolean(payload.active)
                payload.appearance.write(buf)
            },
            { buf ->
                TypingBubblePayload(
                    sourceId = buf.readUUID(),
                    active = buf.readBoolean(),
                    appearance = SpeechBubbleAppearance.read(buf)
                )
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.TYPING_BUBBLE_SERVER
    }
}

data class TypingBubbleBroadcastPayload(
    val sourceId: UUID,
    val active: Boolean,
    val appearance: SpeechBubbleAppearance
) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TypingBubbleBroadcastPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeUUID(payload.sourceId)
                buf.writeBoolean(payload.active)
                payload.appearance.write(buf)
            },
            { buf ->
                TypingBubbleBroadcastPayload(
                    sourceId = buf.readUUID(),
                    active = buf.readBoolean(),
                    appearance = SpeechBubbleAppearance.read(buf)
                )
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.TYPING_BUBBLE_CLIENT
    }
}
