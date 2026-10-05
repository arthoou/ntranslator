package com.arthou.ntranslator.network

 import net.minecraft.network.RegistryFriendlyByteBuf
 import net.minecraft.network.codec.StreamCodec
 import net.minecraft.network.protocol.common.custom.CustomPacketPayload
 import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec
 import com.arthou.ntranslator.network.payloads.*
import com.arthou.ntranslator.NTranslator

object PacketIds {
      val SERVER_SUPPORT = create("server_support", ServerSupportPayload.CODEC)
      val SEND_TRANSCRIPT_TO_CLIENT = create("send_transcript_client", SendTranscriptToClientPayload.CODEC)
      val SEND_TRANSCRIPT_TO_SERVER = create("send_transcript_server", SendTranscriptToServerPayload.CODEC)
      val SEND_CHAT_BUBBLE = create("send_chat_bubble", SendChatBubblePayload.CODEC)
      val SET_USED_LANGUAGES = create("set_used_languages", SetUsedLanguagesPayload.CODEC)
      val MARK_INCOMPLETE = create("mark_incomplete", MarkIncompletePayload.CODEC)
      val TRANSLATE_SIGN = create("translate_sign", TranslateSignPayload.CODEC)
      val SET_CURRENT_LANGUAGE = create("set_current_language", SetCurrentLanguagePayload.CODEC)
      val SYNC_BUBBLE_APPEARANCE = create("sync_bubble_appearance", SyncBubbleAppearancePayload.CODEC)
      val BUBBLE_TTS_STATE = create("bubble_tts_state", BubbleTtsStatePayload.CODEC)
      val BUBBLE_CHAT_STATE = create("bubble_chat_state", BubbleChatStatePayload.CODEC)
      val TYPING_BUBBLE_SERVER = create("typing_bubble_server", TypingBubblePayload.CODEC)
      val TYPING_BUBBLE_CLIENT = create("typing_bubble_client", TypingBubbleBroadcastPayload.CODEC)
      val ADMIN_OPEN_BROWSER = create("admin_open_browser", AdminOpenBrowserPayload.CODEC)
      val ADMIN_OPEN_BUBBLE_CUSTOMIZATION = create("admin_open_bubble_customization", AdminOpenBubbleCustomizationPayload.CODEC)
      val ADMIN_SET_SPOKEN_LANGUAGE = create("admin_set_spoken_language", AdminSetSpokenLanguagePayload.CODEC)
      val FSB_STATE = create("fsb_state", FsbStatePayload.CODEC)
    
      fun init() {
      }
    
      private fun <T : CustomPacketPayload> create(id: String, codec: StreamCodec<RegistryFriendlyByteBuf, T>): TypeAndCodec<RegistryFriendlyByteBuf, T> {
          return TypeAndCodec(CustomPacketPayload.Type(NTranslator.id(id)), codec)
      }
}
