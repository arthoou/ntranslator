package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf

data class BubbleTtsStatePayload(
    val enabled: Boolean
) : LegacyPayload {
    override val id = PacketIds.BUBBLE_TTS_STATE

    override fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(enabled)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): BubbleTtsStatePayload {
            return BubbleTtsStatePayload(buf.readBoolean())
        }
    }
}
