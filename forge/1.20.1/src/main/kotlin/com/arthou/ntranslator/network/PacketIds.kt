package com.arthou.ntranslator.network

import com.arthou.ntranslator.NTranslator
import net.minecraft.resources.ResourceLocation

object PacketIds {
    val SERVER_SUPPORT = create("server_support")
    val SEND_TRANSCRIPT_TO_CLIENT = create("send_transcript_client")
    val SEND_TRANSCRIPT_TO_SERVER = create("send_transcript_server")
    val SEND_CHAT_BUBBLE = create("send_chat_bubble")
    val SET_USED_LANGUAGES = create("set_used_languages")
    val MARK_INCOMPLETE = create("mark_incomplete")
    val TRANSLATE_SIGN = create("translate_sign")
    val SET_CURRENT_LANGUAGE = create("set_current_language")
    val SYNC_BUBBLE_APPEARANCE = create("sync_bubble_appearance")
    val BUBBLE_TTS_STATE = create("bubble_tts_state")

    fun init() {
    }

    private fun create(id: String): ResourceLocation = NTranslator.id(id)
}
