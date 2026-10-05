package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.network.PacketIds
import net.minecraft.network.FriendlyByteBuf

data class SetUsedLanguagesPayload(val languages: List<Language>) : LegacyPayload {
    override val id = PacketIds.SET_USED_LANGUAGES

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(languages.size)
        languages.forEach(buf::writeEnum)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): SetUsedLanguagesPayload {
            val size = buf.readVarInt()
            val languages = ArrayList<Language>(size)
            repeat(size) {
                languages.add(buf.readEnum(Language::class.java))
            }
            return SetUsedLanguagesPayload(languages)
        }
    }
}
