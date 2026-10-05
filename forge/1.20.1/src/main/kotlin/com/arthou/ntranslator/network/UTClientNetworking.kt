package com.arthou.ntranslator.network

import net.minecraft.network.FriendlyByteBuf
import com.arthou.ntranslator.network.payloads.SendChatBubblePayload
import com.arthou.ntranslator.network.payloads.BubbleTtsStatePayload
import com.arthou.ntranslator.network.payloads.MarkIncompletePayload
import com.arthou.ntranslator.network.payloads.SendTranscriptToClientPayload
import com.arthou.ntranslator.network.payloads.SetCurrentLanguagePayload
import com.arthou.ntranslator.network.payloads.SetUsedLanguagesPayload
import com.arthou.ntranslator.network.payloads.SyncBubbleAppearancePayload
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.networking.NetworkManager
import net.minecraft.client.Minecraft
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.client.bubbles.SpeechBubbleManager
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.events.TranscriptEvents
import com.arthou.ntranslator.transcript.Transcript
import com.arthou.ntranslator.translator.TranslatorManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import java.util.*

object UTClientNetworking {
    private const val BUBBLE_TTS_RANGE = 24.0
    private var bubbleTtsEnabled = false

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
        }

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ ->
            NTranslatorClient.connectedServerHasSupport = false
            bubbleTtsEnabled = false
            SpeechBubbleManager.clear()
        }

        registerReceiver(PacketIds.BUBBLE_TTS_STATE) { buf, _ ->
            val payload = BubbleTtsStatePayload.read(buf)
            Minecraft.getInstance().execute {
                bubbleTtsEnabled = payload.enabled
                if (!payload.enabled) {
                    NTranslatorClient.stopBubbleSpeech()
                }
            }
        }

         registerReceiver(PacketIds.SEND_TRANSCRIPT_TO_CLIENT) { buf, ctx ->
            val payload = SendTranscriptToClientPayload.read(buf)
            val client = Minecraft.getInstance()
            client.execute {
                val localPlayer = client.player ?: return@execute
                val level = client.level ?: return@execute
                val sourceId = payload.uuid
                val source = level.getPlayerByUUID(sourceId) ?: return@execute

                val sourceLanguage = payload.language
                val index = payload.index
                val updateTime = payload.updateTime
                val bubbleAppearance = payload.bubbleAppearance
                val bubbleVoice = payload.bubbleVoice
                val boxes = NTranslatorClient.languageBoxes
                val receivedTranslations = linkedMapOf<Language, String>()
                var bubbleUpdated = false

                 for ((language, text) in payload.toSend) {
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
                    box?.updateTranscript(source, text, sourceLanguage, index, updateTime, isPlaceholder)

                    if (box == null && UTVoiceChatCompat.isPlayerAudible(localPlayer)) {
                        TranscriptEvents.UPDATE.invoker().onTranscriptUpdate(Transcript(index, source, text, language, updateTime, isPlaceholder), language)
                    }
                }

                val sourceText = receivedTranslations[sourceLanguage] ?: return@execute
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
                        .whenComplete { translated, error ->
                            if (error != null || translated == null)
                                return@whenComplete

                            client.execute {
                                val currentSource = client.level?.getPlayerByUUID(sourceId) ?: return@execute
                                box.updateTranscript(currentSource, translated, sourceLanguage, index, updateTime, false)

                                if (bubbleEnabled && box.language == bubbleTarget) {
                                    SpeechBubbleManager.update(currentSource, translated, sourceLanguage, index, updateTime, false, bubbleAppearance, false)
                                }
                            }
                        }
                }

                if (bubbleEnabled &&
                    bubbleTarget != sourceLanguage &&
                    !receivedTranslations.containsKey(bubbleTarget) &&
                    boxes.none { it.language == bubbleTarget }
                ) {
                    TranslatorManager.queueTranslation(sourceText, sourceLanguage, bubbleTarget, source, index)
                        .whenComplete { translated, error ->
                            if (error != null || translated == null)
                                return@whenComplete

                            client.execute {
                                val currentSource = client.level?.getPlayerByUUID(sourceId) ?: return@execute
                                SpeechBubbleManager.update(currentSource, translated, sourceLanguage, index, updateTime, false, bubbleAppearance, false)
                            }
                        }
                }
            }
        }

        registerReceiver(PacketIds.SEND_CHAT_BUBBLE) { buf, ctx ->
            val payload = SendChatBubblePayload.read(buf)
            val client = Minecraft.getInstance()
            client.execute {
                val level = client.level ?: return@execute
                val sourceId = payload.sourceId
                val source = level.getPlayerByUUID(sourceId) ?: return@execute

                val sourceLanguage = payload.sourceLanguage
                val index = payload.index
                val updateTime = payload.updateTime
                val bubbleAppearance = payload.bubbleAppearance
                val bubbleVoice = payload.bubbleVoice
                val text = payload.text

                SpeechBubbleManager.update(source, text, sourceLanguage, index, updateTime, false, bubbleAppearance, false)
                maybeSpeakBubble(client.player, source, text, sourceLanguage, bubbleVoice)
            }
        }

         registerReceiver(PacketIds.MARK_INCOMPLETE) { buf, _ ->
            val payload = MarkIncompletePayload.read(buf)
            Minecraft.getInstance().execute {
                val from = payload.from
                val to = payload.to
                val uuid = payload.uuid
                val index = payload.index
                val isIncomplete = payload.isIncomplete

                val box = NTranslatorClient.languageBoxes.firstOrNull { it.language == to }
                box?.transcripts?.firstOrNull { it.language == from && it.player.uuid == uuid && it.index == index }?.incomplete = isIncomplete

                if (to == SpeechBubbleManager.targetLanguage()) {
                    SpeechBubbleManager.markIncomplete(uuid, index, isIncomplete)
                }
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
            Language.SPANISH -> BubbleVoice.SPANISH
            Language.ITALIAN -> BubbleVoice.ITALIAN
            Language.POLISH -> BubbleVoice.POLISH
            else -> BubbleVoice.ENGLISH
        }
    }

    private fun registerReceiver(id: ResourceLocation, receiver: NetworkManager.NetworkReceiver) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, id, receiver)
    }
}
