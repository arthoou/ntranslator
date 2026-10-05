package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.network.PacketIds
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf

data class TranslateSignPayload(val pos: BlockPos) : LegacyPayload {
    override val id = PacketIds.TRANSLATE_SIGN

    override fun write(buf: FriendlyByteBuf) {
        buf.writeBlockPos(pos)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): TranslateSignPayload {
            return TranslateSignPayload(buf.readBlockPos())
        }
    }
}
