package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf

data class SetCurrentLanguagePayload(val language: Language) : LegacyPayload {
    override val id = PacketIds.SET_CURRENT_LANGUAGE

    override fun write(buf: FriendlyByteBuf) {
        buf.writeEnum(language)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): SetCurrentLanguagePayload {
            return SetCurrentLanguagePayload(buf.readEnum(Language::class.java))
        }
    }
}
