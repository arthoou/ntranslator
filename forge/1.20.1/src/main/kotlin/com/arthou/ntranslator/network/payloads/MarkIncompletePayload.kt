package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf
import java.util.UUID

data class MarkIncompletePayload(
    val from: Language,
    val to: Language,
    val uuid: UUID,
    val index: Int,
    val isIncomplete: Boolean
) : LegacyPayload {
    override val id = PacketIds.MARK_INCOMPLETE

    override fun write(buf: FriendlyByteBuf) {
        buf.writeEnum(from)
        buf.writeEnum(to)
        buf.writeUUID(uuid)
        buf.writeVarInt(index)
        buf.writeBoolean(isIncomplete)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): MarkIncompletePayload = MarkIncompletePayload(
            from = buf.readEnum(Language::class.java),
            to = buf.readEnum(Language::class.java),
            uuid = buf.readUUID(),
            index = buf.readVarInt(),
            isIncomplete = buf.readBoolean()
        )
    }
}
