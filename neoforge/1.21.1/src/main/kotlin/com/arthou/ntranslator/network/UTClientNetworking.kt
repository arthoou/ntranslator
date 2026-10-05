package com.arthou.ntranslator.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec
import com.arthou.ntranslator.network.payloads.SendChatBubblePayload
import com.arthou.ntranslator.network.payloads.SetCurrentLanguagePayload
import com.arthou.ntranslator.network.payloads.SetUsedLanguagesPayload
import com.arthou.ntranslator.network.payloads.SyncBubbleAppearancePayload
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.networking.NetworkManager
import net.minecraft.client.Minecraft
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.client.bubbles.ChatTypingBubbleWatcher
import com.arthou.ntranslator.client.bubbles.SpeechBubbleManager
import com.arthou.ntranslator.client.gui.SpeechBubbleCustomizationScreen
import com.arthou.ntranslator.client.transcribers.browser.BrowserSpeechTranscriber
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.events.TranscriptEvents
import com.arthou.ntranslator.transcript.Transcript
import com.arthou.ntranslator.translator.TranslatorManager
import net.minecraft.world.entity.player.Player
import java.util.*

object UTClientNetworking {
    private const val BUBBLE_TTS_RANGE = 24.0
    private var bubbleTtsEnabled = false
    var serverFsbEnabled = true
        private set

    fun init() {
        registerReceiver(PacketIds.SERVER_SUPPORT) { _, _ ->
            NTranslatorClient.connectedServerHasSupport = true
            Minecraft.getInstance().execute {
                syncRequestedLanguages()
                syncBubbleAppearance()
                syncCurrentLanguage()
            }
        }

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { _ ->
            Minecraft.getInstance().execute {
                syncRequestedLanguages()
            }

            Minecraft.getInstance().execute {
                syncBubbleAppearance()
            }

            Minecraft.getInstance().execute {
                syncCurrentLanguage()
            }

            Minecraft.getInstance().execute {
                UTVoiceChatCompat.refreshLocalMuteState()
            }
        }

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ ->
            NTranslatorClient.connectedServerHasSupport = false
            bubbleTtsEnabled = false
            ChatTypingBubbleWatcher.setBubbleOnlyChatActive(false)
            SpeechBubbleManager.clear()
        }

        registerReceiver(PacketIds.BUBBLE_TTS_STATE) { buf, _ ->
            bubbleTtsEnabled = buf.enabled
            if (!buf.enabled) {
                NTranslatorClient.stopBubbleSpeech()
            }
        }

        registerReceiver(PacketIds.BUBBLE_CHAT_STATE) { buf, _ ->
            ChatTypingBubbleWatcher.setBubbleOnlyChatActive(buf.enabled)
        }

        registerReceiver(PacketIds.FSB_STATE) { buf, _ ->
            serverFsbEnabled = buf.enabled
        }

        registerReceiver(PacketIds.TYPING_BUBBLE_CLIENT) { buf, _ ->
            val level = Minecraft.getInstance().level ?: return@registerReceiver
            val source = level.getPlayerByUUID(buf.sourceId) ?: return@registerReceiver

            if (buf.active) {
                SpeechBubbleManager.showTyping(source, buf.appearance)
            } else {
                SpeechBubbleManager.clearTyping(buf.sourceId)
            }
        }

         registerReceiver(PacketIds.SEND_TRANSCRIPT_TO_CLIENT) { buf, ctx ->
            val localPlayer = Minecraft.getInstance().player ?: return@registerReceiver
            val level = Minecraft.getInstance().level ?: return@registerReceiver
            val sourceId = buf.uuid
            val source = level.getPlayerByUUID(sourceId) ?: return@registerReceiver

            val sourceLanguage = buf.language
            val index = buf.index
            val updateTime = buf.updateTime
            val bubbleAppearance = buf.bubbleAppearance
            val bubbleVoice = buf.bubbleVoice
            val ignoreTranscriptRange = buf.ignoreTranscriptRange

            val boxes = NTranslatorClient.languageBoxes
            val receivedTranslations = linkedMapOf<Language, String>()
            var bubbleUpdated = false

             for ((language, text) in buf.toSend) {
                val isPlaceholder = text.trim() == "..."
                receivedTranslations[language] = text

                if (language == SpeechBubbleManager.targetLanguage()) {
                    SpeechBubbleManager.update(source, text, sourceLanguage, index, updateTime, isPlaceholder, bubbleAppearance, false)
                    maybeSpeakBubble(localPlayer, source, text, language, bubbleVoice)
                    bubbleUpdated = true
                }

                if (language == NTranslatorClient.transcriber.language && sourceId == localPlayer.uuid)
                    continue

                val box = boxes.firstOrNull { it.language == language }
                box?.updateTranscript(source, text, sourceLanguage, index, updateTime, isPlaceholder, ignoreTranscriptRange)

                if (box == null && UTVoiceChatCompat.isPlayerAudible(localPlayer)) {
                    TranscriptEvents.UPDATE.invoker().onTranscriptUpdate(Transcript(index, source, text, language, updateTime, isPlaceholder), language)
                }
            }

            val sourceText = receivedTranslations[sourceLanguage] ?: return@registerReceiver
            val bubbleTarget = SpeechBubbleManager.targetLanguage()
            val bubbles = NTranslator.config.client.speechBubbles
            val bubbleEnabled = bubbles.enabled || (sourceId == localPlayer.uuid && bubbles.showOwnBubble)
            if (bubbleEnabled && !bubbleUpdated) {
                val fallbackBubbleText = when {
                    bubbleTarget == sourceLanguage -> sourceText
                    receivedTranslations.containsKey(bubbleTarget) -> receivedTranslations[bubbleTarget]
                    else -> receivedTranslations.entries
                        .firstOrNull { (language, text) -> language != sourceLanguage && text.isNotBlank() && text.trim() != "..." }
                        ?.value
                }
                if (!fallbackBubbleText.isNullOrBlank()) {
                    SpeechBubbleManager.update(
                        source,
                        fallbackBubbleText,
                        sourceLanguage,
                        index,
                        updateTime,
                        fallbackBubbleText.trim() == "...",
                        bubbleAppearance,
                        false
                    )
                    maybeSpeakBubble(localPlayer, source, fallbackBubbleText, bubbleTarget, bubbleVoice)
                    bubbleUpdated = true
                }
            }

            for (box in boxes) {
                if (box.language == sourceLanguage || receivedTranslations.containsKey(box.language))
                    continue

                TranslatorManager.queueTranslation(sourceText, sourceLanguage, box.language, source, index)
                    .whenCompleteAsync { translated, error ->
                        if (error != null || translated == null)
                            return@whenCompleteAsync

                        box.updateTranscript(source, translated, sourceLanguage, index, updateTime, false, ignoreTranscriptRange)

                        if (bubbleEnabled && box.language == bubbleTarget) {
                            SpeechBubbleManager.update(source, translated, sourceLanguage, index, updateTime, false, bubbleAppearance, false)
                        }
                    }
            }

            if (bubbleEnabled &&
                bubbleTarget != sourceLanguage &&
                !receivedTranslations.containsKey(bubbleTarget) &&
                boxes.none { it.language == bubbleTarget }
            ) {
                TranslatorManager.queueTranslation(sourceText, sourceLanguage, bubbleTarget, source, index)
                    .whenCompleteAsync { translated, error ->
                        if (error != null || translated == null)
                            return@whenCompleteAsync

                        SpeechBubbleManager.update(source, translated, sourceLanguage, index, updateTime, false, bubbleAppearance, false)
                    }
            }
        }

        registerReceiver(PacketIds.SEND_CHAT_BUBBLE) { buf, ctx ->
            val level = Minecraft.getInstance().level ?: return@registerReceiver
            val sourceId = buf.sourceId
            val source = level.getPlayerByUUID(sourceId) ?: return@registerReceiver

            val sourceLanguage = buf.sourceLanguage
            val index = buf.index
            val updateTime = buf.updateTime
            val bubbleAppearance = buf.bubbleAppearance
            val bubbleVoice = buf.bubbleVoice
            val text = buf.text

            SpeechBubbleManager.update(source, text, sourceLanguage, index, updateTime, false, bubbleAppearance, false)
            maybeSpeakBubble(Minecraft.getInstance().player, source, text, sourceLanguage, bubbleVoice)
        }

         registerReceiver(PacketIds.MARK_INCOMPLETE) { buf, _ ->
            val from = buf.from
            val to = buf.to
            val uuid = buf.uuid
            val index = buf.index
            val isIncomplete = buf.isIncomplete

            val box = NTranslatorClient.languageBoxes.firstOrNull { it.language == to }
            box?.transcripts?.firstOrNull { it.language == from && it.player.uuid == uuid && it.index == index }?.incomplete = isIncomplete

            if (to == SpeechBubbleManager.targetLanguage()) {
                SpeechBubbleManager.markIncomplete(uuid, index, isIncomplete)
            }
        }

        registerReceiver(PacketIds.ADMIN_OPEN_BROWSER) { buf, _ ->
            Minecraft.getInstance().execute {
                (NTranslatorClient.transcriber as? BrowserSpeechTranscriber)?.openFromAdmin(buf.hidden, buf.browser)
            }
        }

        registerReceiver(PacketIds.ADMIN_OPEN_BUBBLE_CUSTOMIZATION) { _, _ ->
            Minecraft.getInstance().execute {
                Minecraft.getInstance().player?.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Opening NEXEL bubble customization..."),
                    true
                )
                Minecraft.getInstance().setScreen(SpeechBubbleCustomizationScreen(Minecraft.getInstance().screen))
            }
        }

        registerReceiver(PacketIds.ADMIN_SET_SPOKEN_LANGUAGE) { buf, _ ->
            Minecraft.getInstance().execute {
                val language = buf.language
                if (!language.supportedTranscribers.containsKey(NTranslator.config.client.transcriber)) {
                    Minecraft.getInstance().player?.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("NEXEL: current transcriber does not support ${language.code.uppercase(Locale.ROOT)}."),
                        false
                    )
                    return@execute
                }

                NTranslator.config.client.spokenLanguage = language
                NTranslator.config.client.language = language
                NTranslatorClient.transcriber.changeLanguage(language)
                NTranslator.saveConfig()
                syncCurrentLanguage()

                Minecraft.getInstance().player?.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("NEXEL spoken language set to ${language.text.string}."),
                    true
                )
            }
        }
    }

    fun syncRequestedLanguages() {
        if (Minecraft.getInstance().player == null)
            return

        if (!NTranslatorClient.connectedServerHasSupport)
            return

        NTranslator.instance.proxy.sendPacketClient(SetUsedLanguagesPayload(requestedLanguages().toList()))
    }

    fun syncBubbleAppearance() {
        if (Minecraft.getInstance().player == null)
            return

        if (!NTranslatorClient.connectedServerHasSupport)
            return

        NTranslator.instance.proxy.sendPacketClient(
            SyncBubbleAppearancePayload(
                SpeechBubbleAppearance.fromConfig(NTranslator.config.client.speechBubbles)
            )
        )
    }

    fun syncCurrentLanguage() {
        if (Minecraft.getInstance().player == null)
            return

        if (!NTranslatorClient.connectedServerHasSupport)
            return

        NTranslator.instance.proxy.sendPacketClient(SetCurrentLanguagePayload(NTranslator.config.client.spokenLanguage))
    }

    private fun requestedLanguages(): EnumSet<Language> {
        val requested = EnumSet.noneOf(Language::class.java)
        requested.addAll(NTranslatorClient.languageBoxes.map { it.language })

        val bubbles = NTranslator.config.client.speechBubbles
        if (bubbles.enabled || bubbles.showOwnBubble) {
            requested.add(NTranslator.config.client.subtitleLanguage)
        }

        return requested
    }

    private fun maybeSpeakBubble(localPlayer: Player?, source: Player, text: String, language: Language, voice: BubbleVoice) {
        if (!bubbleTtsEnabled || localPlayer == null || voice == BubbleVoice.OFF || text.isBlank() || text.trim() == "...") {
            return
        }

        if (localPlayer.distanceToSqr(source) > (BUBBLE_TTS_RANGE * BUBBLE_TTS_RANGE)) {
            return
        }

        NTranslatorClient.speakBubbleText(text, language, voice)
    }

    private fun voiceFor(language: Language): BubbleVoice {
        return when (language) {
            Language.PORTUGUESE -> BubbleVoice.BRAZIL
            Language.PORTUGUESE_PORTUGAL -> BubbleVoice.BRAZIL
            Language.SPANISH -> BubbleVoice.SPANISH
            Language.ITALIAN -> BubbleVoice.ITALIAN
            Language.POLISH -> BubbleVoice.POLISH
            else -> BubbleVoice.ENGLISH
        }
    }

    private fun <T : CustomPacketPayload> registerReceiver(type: TypeAndCodec<RegistryFriendlyByteBuf, T>, receiver: NetworkManager.NetworkReceiver<T>) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, type.type, type.codec, receiver)
    }
}
