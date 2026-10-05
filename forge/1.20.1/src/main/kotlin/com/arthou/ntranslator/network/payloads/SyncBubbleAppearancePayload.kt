package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf

data class SyncBubbleAppearancePayload(val appearance: SpeechBubbleAppearance) : LegacyPayload {
    override val id = PacketIds.SYNC_BUBBLE_APPEARANCE

    override fun write(buf: FriendlyByteBuf) {
        appearance.write(buf)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): SyncBubbleAppearancePayload {
            return SyncBubbleAppearancePayload(SpeechBubbleAppearance.read(buf))
        }
    }
}
