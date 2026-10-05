package com.arthou.ntranslator.network

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.NTranslator.Companion.hasVoiceChat
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.config.SpeechBubbleConfig
import com.arthou.ntranslator.network.payloads.MarkIncompletePayload
import com.arthou.ntranslator.network.payloads.BubbleTtsStatePayload
import com.arthou.ntranslator.network.payloads.SendChatBubblePayload
import com.arthou.ntranslator.network.payloads.SendTranscriptToClientPayload
import com.arthou.ntranslator.network.payloads.SendTranscriptToServerPayload
import com.arthou.ntranslator.network.payloads.ServerSupportPayload
import com.arthou.ntranslator.network.payloads.SetCurrentLanguagePayload
import com.arthou.ntranslator.network.payloads.SetUsedLanguagesPayload
import com.arthou.ntranslator.network.payloads.SyncBubbleAppearancePayload
import com.arthou.ntranslator.network.payloads.TranslateSignPayload
import com.arthou.ntranslator.translator.TranslatorManager
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.networking.NetworkManager
import net.minecraft.ChatFormatting
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.level.block.entity.SignBlockEntity
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object UTServerNetworking {
    private val proxy = NTranslator.instance.proxy

    val playerLanguages = ConcurrentHashMap<UUID, Language>()
    private val usedLanguages = ConcurrentHashMap<UUID, EnumSet<Language>>()
    private val playerBubbleAppearances = ConcurrentHashMap<UUID, SpeechBubbleAppearance>()
    private val playerBubbleVoices = ConcurrentHashMap<UUID, BubbleVoice>()
    private val bubbleChatPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val bubbleTtsPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val bubbleChatIndices = ConcurrentHashMap<UUID, AtomicInteger>()
    private val playerStateFile = File(proxy.configDir.toFile(), "nexel-player-settings.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun init() {
        loadPlayerSettings()
        PacketIds.init()
        registerReceiver(PacketIds.SET_USED_LANGUAGES) { buf, ctx ->
            val payload = SetUsedLanguagesPayload.read(buf)
            usedLanguages[ctx.player.uuid] = EnumSet.noneOf(Language::class.java).apply {
                addAll(payload.languages)
            }
        }

        registerReceiver(PacketIds.SEND_TRANSCRIPT_TO_SERVER) { buf, ctx ->
            val payload = SendTranscriptToServerPayload.read(buf)
            val sourceLanguage = payload.sourceLanguage
            val text = payload.text
            val index = payload.index
            val updateTime = payload.updateTime
            val bubbleAppearance = payload.bubbleAppearance
            val isFinal = payload.isFinal

            if (!canPlayerRequestTranslations(ctx.player)) {
                return@registerReceiver
            }

            if (text.length > 1500) {
                ctx.player.displayClientMessage(
                    Component.literal("Transcription too long! Current transcript discarded.")
                        .withStyle(ChatFormatting.RED),
                    true
                )
                proxy.sendPacketServer(
                    ctx.player as ServerPlayer,
                    MarkIncompletePayload(sourceLanguage, sourceLanguage, ctx.player.uuid, index, true)
                )
                return@registerReceiver
            }

            val sourcePlayer = ctx.player as ServerPlayer
            val shouldUseGooglePlaceholder = !NTranslator.config.server.useLibreTranslate &&
                (!sourcePlayer.server.isDedicatedServer || !TranslatorManager.hasReadyInstance())

            if (shouldUseGooglePlaceholder) {
                handleGoogleTranscript(
                    ctx,
                    sourcePlayer,
                    sourceLanguage,
                    text,
                    index,
                    updateTime,
                    bubbleAppearance,
                    isFinal
                )
                return@registerReceiver
            }

            val translations = ConcurrentHashMap<Language, String>()
            val translationsToSend = ConcurrentLinkedDeque<Language>()
            val requestedLanguages = requestedLanguages(sourceLanguage)

            requestedLanguages
                .map { language ->
                    language to if (language == sourceLanguage) {
                        CompletableFuture.completedFuture(text)
                    } else {
                        TranslatorManager.queueTranslation(text, sourceLanguage, language, ctx.player, index)
                    }
                }
                .forEach { (language, future) ->
                    future.whenCompleteAsync { translated, error ->
                        if (error != null) {
                            return@whenCompleteAsync
                        }

                        translations[language] = translated ?: text
                        translationsToSend.add(language)

                        ctx.queue {
                            if (translationsToSend.isNotEmpty()) {
                                broadcastTranslations(
                                    ctx.player as ServerPlayer,
                                    sourceLanguage,
                                    index,
                                    updateTime,
                                    bubbleAppearance,
                                    translationsToSend,
                                    translations
                                )
                                translationsToSend.clear()
                            }
                        }
                    }
                }
        }

        registerReceiver(PacketIds.SET_CURRENT_LANGUAGE) { buf, ctx ->
            val payload = SetCurrentLanguagePayload.read(buf)
            playerLanguages[ctx.player.uuid] = payload.language
        }

        registerReceiver(PacketIds.SYNC_BUBBLE_APPEARANCE) { buf, ctx ->
            val payload = SyncBubbleAppearancePayload.read(buf)
            playerBubbleAppearances[ctx.player.uuid] = payload.appearance
        }

        registerReceiver(PacketIds.TRANSLATE_SIGN) { buf, ctx ->
            val payload = TranslateSignPayload.read(buf)
            if (!canPlayerRequestTranslations(ctx.player)) {
                return@registerReceiver
            }

            val player = ctx.player
            val level = player.level()
            val state = level.getBlockState(payload.pos)

            if (state.block !is SignBlock) {
                return@registerReceiver
            }

            ctx.queue {
                val entity = level.getBlockEntity(payload.pos)
                if (entity !is SignBlockEntity) {
                    return@queue
                }

                val text = (if (entity.isFacingFrontText(player)) entity.frontText else entity.backText)
                    .getMessages(false)
                    .joinToString("\n") { it.string }
                    .trim()

                if (text.isBlank()) {
                    return@queue
                }

                val language = TranslatorManager.detectLanguage(text) ?: Language.ENGLISH
                val targetLanguage = playerLanguages.getOrElse(player.uuid) { Language.ENGLISH }
                val translatedText = TranslatorManager.translateLine(text, language, targetLanguage) ?: text

                player.displayClientMessage(
                    Component.empty()
                        .append(Component.literal("[NTranslator]: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.translatable("ntranslator.transcribe_sign", language.text, targetLanguage.text, translatedText)),
                    false
                )
            }
        }

        PlayerEvent.PLAYER_JOIN.register { player ->
            proxy.sendPacketServer(player, ServerSupportPayload.EMPTY)
            syncBubbleTts(player, bubbleTtsPlayers.contains(player.uuid))
        }

        PlayerEvent.PLAYER_QUIT.register { player ->
            usedLanguages.remove(player.uuid)
        }
    }

    private fun handleGoogleTranscript(
        ctx: NetworkManager.PacketContext,
        source: ServerPlayer,
        sourceLanguage: Language,
        text: String,
        index: Int,
        updateTime: Long,
        bubbleAppearance: SpeechBubbleAppearance,
        isFinal: Boolean
    ) {
        val requestedLanguages = requestedLanguages(sourceLanguage)
        val placeholderTranslations = ConcurrentHashMap<Language, String>().apply {
            requestedLanguages.forEach { language ->
                put(language, if (language == sourceLanguage) text else "...")
            }
        }

        broadcastTranslations(
            source,
            sourceLanguage,
            index,
            updateTime,
            bubbleAppearance,
            ConcurrentLinkedDeque(requestedLanguages.toList()),
            placeholderTranslations
        )

        if (!isFinal) {
            return
        }

        val translations = ConcurrentHashMap<Language, String>()
        val translationsToSend = ConcurrentLinkedDeque<Language>()

        requestedLanguages
            .map { language ->
                language to if (language == sourceLanguage) {
                    CompletableFuture.completedFuture(text)
                } else {
                    TranslatorManager.queueTranslation(text, sourceLanguage, language, source, index)
                }
            }
            .forEach { (language, future) ->
                future.whenCompleteAsync { translated, error ->
                    if (error != null) {
                        return@whenCompleteAsync
                    }

                    translations[language] = translated ?: text
                    translationsToSend.add(language)

                    ctx.queue {
                        if (translationsToSend.isNotEmpty()) {
                            broadcastTranslations(
                                source,
                                sourceLanguage,
                                index,
                                updateTime,
                                bubbleAppearance,
                                translationsToSend,
                                translations
                            )
                            translationsToSend.clear()
                        }
                    }
                }
            }
    }

    private fun requestedLanguages(sourceLanguage: Language): EnumSet<Language> {
        return EnumSet.noneOf(Language::class.java).apply {
            add(sourceLanguage)
            addAll(Language.entries.filter { language -> usedLanguages.values.any { it.contains(language) } })
            addAll(playerLanguages.values)
        }
    }

    private fun broadcastTranslations(
        source: ServerPlayer,
        sourceLanguage: Language,
        index: Int,
        updateTime: Long,
        bubbleAppearance: SpeechBubbleAppearance,
        translationsToSend: ConcurrentLinkedDeque<Language>,
        translations: ConcurrentHashMap<Language, String>
    ) {
        val toSend = translations.filter { translationsToSend.contains(it.key) }

        if (hasVoiceChat) {
            val nearby = UTVoiceChatCompat.getNearbyPlayers(source).ifEmpty { connectedPlayers(source) }
            for (player in nearby) {
                if (UTVoiceChatCompat.isPlayerDeafened(player) && player != source) {
                    continue
                }

                proxy.sendPacketServer(
                    player,
                    SendTranscriptToClientPayload(
                        source.uuid,
                        sourceLanguage,
                        index,
                        updateTime,
                        bubbleAppearance,
                        bubbleVoiceFor(source, player),
                        toSend
                    )
                )
            }
        } else {
            for (player in connectedPlayers(source)) {
                proxy.sendPacketServer(
                    player,
                    SendTranscriptToClientPayload(
                        source.uuid,
                        sourceLanguage,
                        index,
                        updateTime,
                        bubbleAppearance,
                        bubbleVoiceFor(source, player),
                        toSend
                    )
                )
            }
        }
    }

    fun toggleBubbleChat(target: ServerPlayer): Boolean {
        if (!bubbleChatPlayers.add(target.uuid)) {
            bubbleChatPlayers.remove(target.uuid)
            savePlayerSettings()
            return false
        }

        savePlayerSettings()
        return true
    }

    fun isBubbleChatEnabled(player: ServerPlayer): Boolean {
        return bubbleChatPlayers.contains(player.uuid)
    }

    fun setBubbleVoice(target: ServerPlayer, voice: BubbleVoice) {
        playerBubbleVoices[target.uuid] = voice
        savePlayerSettings()
    }

    fun toggleBubbleTts(target: ServerPlayer): Boolean {
        val enabled = if (!bubbleTtsPlayers.add(target.uuid)) {
            bubbleTtsPlayers.remove(target.uuid)
            false
        } else {
            true
        }

        savePlayerSettings()
        syncBubbleTts(target, enabled)
        return enabled
    }

    fun handleBubbleChat(source: ServerPlayer, rawText: String) {
        if (rawText.isBlank()) {
            return
        }

        val sourceLanguage = TranslatorManager.detectLanguage(rawText)
            ?: playerLanguages[source.uuid]
            ?: Language.ENGLISH
        val index = bubbleChatIndices.computeIfAbsent(source.uuid) { AtomicInteger(1_000_000) }.getAndIncrement()
        val updateTime = System.currentTimeMillis()
        val appearance = playerBubbleAppearances[source.uuid] ?: SpeechBubbleAppearance.fromConfig(SpeechBubbleConfig())

        broadcastChatBubble(source, rawText, sourceLanguage, index, updateTime, appearance)
    }

    private fun broadcastChatBubble(
        source: ServerPlayer,
        text: String,
        sourceLanguage: Language,
        index: Int,
        updateTime: Long,
        bubbleAppearance: SpeechBubbleAppearance
    ) {
        val viewers = if (hasVoiceChat) {
            UTVoiceChatCompat.getNearbyPlayers(source).ifEmpty { connectedPlayers(source) }
        } else {
            connectedPlayers(source)
        }

        for (viewer in viewers) {
            if (viewer.hasDisconnected()) {
                continue
            }

            if (hasVoiceChat && UTVoiceChatCompat.isPlayerDeafened(viewer) && viewer != source) {
                continue
            }

            val targetLanguage = playerLanguages[viewer.uuid] ?: sourceLanguage
            proxy.sendPacketServer(
                viewer,
                SendChatBubblePayload(
                    source.uuid,
                    sourceLanguage,
                    targetLanguage,
                    index,
                    updateTime,
                    bubbleAppearance,
                    bubbleVoiceFor(source, viewer),
                    text
                )
            )
        }
    }

    private fun bubbleVoiceFor(source: ServerPlayer, viewer: ServerPlayer): BubbleVoice {
        if (!bubbleTtsPlayers.contains(viewer.uuid)) {
            return BubbleVoice.OFF
        }

        return playerBubbleVoices[source.uuid]?.takeIf { it != BubbleVoice.OFF } ?: BubbleVoice.ENGLISH
    }

    private fun connectedPlayers(source: ServerPlayer): List<ServerPlayer> {
        return source.server.playerList.players.filterNot(ServerPlayer::hasDisconnected)
    }

    private fun syncBubbleTts(player: ServerPlayer, enabled: Boolean) {
        proxy.sendPacketServer(player, BubbleTtsStatePayload(enabled))
    }

    fun canPlayerRequestTranslations(player: Player): Boolean {
        return NTranslator.instance.proxy.hasTranscriptPermission(player)
    }

    private fun savePlayerSettings() {
        try {
            if (!playerStateFile.exists()) {
                playerStateFile.parentFile?.mkdirs()
                playerStateFile.createNewFile()
            }

            val settings = mutableMapOf<String, PersistedPlayerSettings>()
            val uuids = mutableSetOf<UUID>()
            uuids.addAll(bubbleChatPlayers)
            uuids.addAll(playerBubbleVoices.keys)
            uuids.addAll(bubbleTtsPlayers)

            for (uuid in uuids) {
                settings[uuid.toString()] = PersistedPlayerSettings(
                    bubbleChat = bubbleChatPlayers.contains(uuid),
                    bubbleTts = bubbleTtsPlayers.contains(uuid),
                    bubbleVoice = playerBubbleVoices[uuid] ?: BubbleVoice.OFF
                )
            }

            playerStateFile.writeText(gson.toJson(settings))
        } catch (e: Exception) {
            NTranslator.logger.error("Failed to save NEXEL player settings!")
            e.printStackTrace()
        }
    }

    private fun loadPlayerSettings() {
        if (!playerStateFile.exists()) {
            return
        }

        try {
            val type = object : TypeToken<Map<String, PersistedPlayerSettings>>() {}.type
            val settings: Map<String, PersistedPlayerSettings> = gson.fromJson(playerStateFile.readText(), type) ?: emptyMap()

            bubbleChatPlayers.clear()
            bubbleTtsPlayers.clear()
            playerBubbleVoices.clear()

            for ((uuidString, state) in settings) {
                val uuid = runCatching { UUID.fromString(uuidString) }.getOrNull() ?: continue
                if (state.bubbleChat) {
                    bubbleChatPlayers.add(uuid)
                }
                if (state.bubbleTts) {
                    bubbleTtsPlayers.add(uuid)
                }
                playerBubbleVoices[uuid] = state.bubbleVoice ?: BubbleVoice.OFF
            }
        } catch (e: Exception) {
            NTranslator.logger.error("Failed to load NEXEL player settings!")
            e.printStackTrace()
        }
    }

    private data class PersistedPlayerSettings(
        val bubbleChat: Boolean = false,
        val bubbleTts: Boolean = false,
        val bubbleVoice: BubbleVoice? = BubbleVoice.OFF
    )

    private fun registerReceiver(
        id: ResourceLocation,
        receiver: NetworkManager.NetworkReceiver
    ) {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, id, receiver)
    }
}
