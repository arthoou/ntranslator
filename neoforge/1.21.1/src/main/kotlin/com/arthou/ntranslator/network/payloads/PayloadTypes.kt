package com.arthou.ntranslator.network.payloads

import com.arthou.ntranslator.NTranslator
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

object PayloadTypes {
    val SERVER_SUPPORT = CustomPacketPayload.Type<ServerSupportPayload>(NTranslator.id("server_support"))
    val SEND_TRANSCRIPT_TO_CLIENT = CustomPacketPayload.Type<SendTranscriptToClientPayload>(NTranslator.id("send_transcript_client"))
    val SEND_TRANSCRIPT_TO_SERVER = CustomPacketPayload.Type<SendTranscriptToServerPayload>(NTranslator.id("send_transcript_server"))
    val SET_USED_LANGUAGES = CustomPacketPayload.Type<SetUsedLanguagesPayload>(NTranslator.id("set_used_languages"))
    val MARK_INCOMPLETE = CustomPacketPayload.Type<MarkIncompletePayload>(NTranslator.id("mark_incomplete"))
    val TRANSLATE_SIGN = CustomPacketPayload.Type<TranslateSignPayload>(NTranslator.id("translate_sign"))
    val SET_CURRENT_LANGUAGE = CustomPacketPayload.Type<SetCurrentLanguagePayload>(NTranslator.id("set_current_language"))
    val SYNC_BUBBLE_APPEARANCE = CustomPacketPayload.Type<SyncBubbleAppearancePayload>(NTranslator.id("sync_bubble_appearance"))
    val SEND_CHAT_BUBBLE = CustomPacketPayload.Type<SendChatBubblePayload>(NTranslator.id("send_chat_bubble"))
    val BUBBLE_TTS_STATE = CustomPacketPayload.Type<BubbleTtsStatePayload>(NTranslator.id("bubble_tts_state"))
    val BUBBLE_CHAT_STATE = CustomPacketPayload.Type<BubbleChatStatePayload>(NTranslator.id("bubble_chat_state"))
    val TYPING_BUBBLE_SERVER = CustomPacketPayload.Type<TypingBubblePayload>(NTranslator.id("typing_bubble_server"))
    val TYPING_BUBBLE_CLIENT = CustomPacketPayload.Type<TypingBubbleBroadcastPayload>(NTranslator.id("typing_bubble_client"))
    val ADMIN_OPEN_BROWSER = CustomPacketPayload.Type<AdminOpenBrowserPayload>(NTranslator.id("admin_open_browser"))
    val ADMIN_OPEN_BUBBLE_CUSTOMIZATION = CustomPacketPayload.Type<AdminOpenBubbleCustomizationPayload>(NTranslator.id("admin_open_bubble_customization"))
    val ADMIN_SET_SPOKEN_LANGUAGE = CustomPacketPayload.Type<AdminSetSpokenLanguagePayload>(NTranslator.id("admin_set_spoken_language"))
    val FSB_STATE = CustomPacketPayload.Type<FsbStatePayload>(NTranslator.id("fsb_state"))
}
