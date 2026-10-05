package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

data class MarkIncompletePayload(
    val from: Language,
    val to: Language,
    val uuid: UUID,
    val index: Int,
    val isIncomplete: Boolean
) : CustomPacketPayload {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, MarkIncompletePayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeEnum(payload.from)
                buf.writeEnum(payload.to)
                buf.writeUUID(payload.uuid)
                buf.writeVarInt(payload.index)
                buf.writeBoolean(payload.isIncomplete)
            },
            { buf ->
                MarkIncompletePayload(
                    from = buf.readEnum(Language::class.java),
                    to = buf.readEnum(Language::class.java),
                    uuid = buf.readUUID(),
                    index = buf.readVarInt(),
                    isIncomplete = buf.readBoolean()
                )
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PayloadTypes.MARK_INCOMPLETE
    }
}
