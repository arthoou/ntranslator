package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf

object ServerSupportPayload : LegacyPayload {
    override val id = PacketIds.SERVER_SUPPORT

    val EMPTY: ServerSupportPayload = ServerSupportPayload

    override fun write(buf: FriendlyByteBuf) {
    }

    fun read(buf: FriendlyByteBuf): ServerSupportPayload {
        return ServerSupportPayload
    }
}
