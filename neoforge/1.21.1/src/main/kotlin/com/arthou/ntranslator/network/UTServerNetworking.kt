package com.arthou.ntranslator.network

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.NTranslator.Companion.hasVoiceChat
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.config.SpeechBubbleConfig
import com.arthou.ntranslator.fsb.FSBReplacer
import com.arthou.ntranslator.network.payloads.BubbleChatStatePayload
import com.arthou.ntranslator.network.payloads.FsbStatePayload
import com.arthou.ntranslator.network.payloads.MarkIncompletePayload
import com.arthou.ntranslator.network.payloads.BubbleTtsStatePayload
import com.arthou.ntranslator.network.payloads.SendChatBubblePayload
import com.arthou.ntranslator.network.payloads.SendTranscriptToClientPayload
import com.arthou.ntranslator.network.payloads.ServerSupportPayload
import com.arthou.ntranslator.network.payloads.TypingBubbleBroadcastPayload
import com.arthou.ntranslator.network.payloads.TypingBubblePayload
import com.arthou.ntranslator.translator.TranslatorManager
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.networking.NetworkManager
import net.minecraft.ChatFormatting
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec
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
    private const val FALLBACK_BUBBLE_RANGE = 64.0

    private val proxy = NTranslator.instance.proxy

    val playerLanguages = ConcurrentHashMap<UUID, Language>()
    private val usedLanguages = ConcurrentHashMap<UUID, EnumSet<Language>>()
    private val playerBubbleAppearances = ConcurrentHashMap<UUID, SpeechBubbleAppearance>()
    private val playerBubbleVoices = ConcurrentHashMap<UUID, BubbleVoice>()
    private val bubbleChatPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val externalTypingIndicatorPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val bubbleTtsPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val bubbleChatIndices = ConcurrentHashMap<UUID, AtomicInteger>()
    private val playerStateFile = File(proxy.configDir.toFile(), "nexel-player-settings.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun init() {
        loadPlayerSettings()
        PacketIds.init()
        if (!proxy.isClient()) {
            registerServerToClientPayloads()
        }

        registerReceiver(PacketIds.SET_USED_LANGUAGES) { buf, ctx ->
            usedLanguages[ctx.player.uuid] = if (buf.languages.isEmpty()) {
                EnumSet.noneOf(Language::class.java)
            } else {
                EnumSet.copyOf(buf.languages)
            }
        }

        registerReceiver(PacketIds.SEND_TRANSCRIPT_TO_SERVER) { buf, ctx ->
            val sourceLanguage = buf.sourceLanguage
            val text = FSBReplacer.apply(buf.text, sourceLanguage)
            val index = buf.index
            val updateTime = buf.updateTime
            val bubbleAppearance = buf.bubbleAppearance
            val isFinal = buf.isFinal

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
                !TranslatorManager.hasReadyInstance()

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
            val requestedLanguages = requestedLanguages(sourcePlayer, sourceLanguage)

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
            playerLanguages[ctx.player.uuid] = buf.language
        }

        registerReceiver(PacketIds.SYNC_BUBBLE_APPEARANCE) { buf, ctx ->
            playerBubbleAppearances[ctx.player.uuid] = buf.appearance
        }

        registerReceiver(PacketIds.TYPING_BUBBLE_SERVER) { buf, ctx ->
            val source = ctx.player as? ServerPlayer ?: return@registerReceiver
            broadcastTypingBubble(source, buf.active, buf.appearance)
        }

        registerReceiver(PacketIds.TRANSLATE_SIGN) { buf, ctx ->
            if (!canPlayerRequestTranslations(ctx.player)) {
                return@registerReceiver
            }

            val player = ctx.player
            val level = player.level()
            val state = level.getBlockState(buf.pos)

            if (state.block !is SignBlock) {
                return@registerReceiver
            }

            ctx.queue {
                val entity = level.getBlockEntity(buf.pos)
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
            proxy.sendPacketServer(player, FsbStatePayload(NTranslator.config.server.fsbEnabled))
            syncBubbleTts(player, bubbleTtsPlayers.contains(player.uuid))
            syncBubbleChatState(player)
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
        val requestedLanguages = requestedLanguages(source, sourceLanguage)
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

    private fun requestedLanguages(source: ServerPlayer, sourceLanguage: Language): EnumSet<Language> {
        return EnumSet.noneOf(Language::class.java).apply {
            add(sourceLanguage)
            for (viewer in translationViewers(source)) {
                usedLanguages[viewer.uuid]?.let { addAll(it) }
                playerLanguages[viewer.uuid]?.let { add(it) }
            }
            // Preserve translations already requested by clients whose state was synced
            // before voice-chat proximity was available.
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

        for (player in translationViewers(source)) {
            if (hasVoiceChat && UTVoiceChatCompat.isPlayerDeafened(player) && player != source) {
                continue
            }

            val viewerAppearance = if (player.uuid == source.uuid) {
                bubbleAppearance
            } else {
                bubbleAppearance.copy(visible = true)
            }

            proxy.sendPacketServer(
                player,
                SendTranscriptToClientPayload(
                    source.uuid,
                    sourceLanguage,
                    index,
                    updateTime,
                    viewerAppearance,
                    bubbleVoiceFor(source, player),
                    toSend,
                    hasVoiceChat && shouldIgnoreTranscriptRange(source, player)
                )
            )
        }
    }

    private fun shouldIgnoreTranscriptRange(source: ServerPlayer, viewer: ServerPlayer): Boolean {
        if (source == viewer) {
            return false
        }

        return source.distanceToSqr(viewer) > TRANSCRIPT_BOX_RANGE_SQR &&
            UTVoiceChatCompat.playerSharesGroup(source, viewer)
    }

    fun toggleBubbleChat(target: ServerPlayer): Boolean {
        if (!bubbleChatPlayers.add(target.uuid)) {
            bubbleChatPlayers.remove(target.uuid)
            savePlayerSettings()
            syncBubbleChatState(target)
            return false
        }

        savePlayerSettings()
        syncBubbleChatState(target)
        return true
    }

    fun isBubbleChatEnabled(player: ServerPlayer): Boolean {
        return bubbleChatPlayers.contains(player.uuid)
    }

    fun setTypingIndicatorMode(target: ServerPlayer, enabled: Boolean) {
        if (enabled) {
            externalTypingIndicatorPlayers.add(target.uuid)
        } else {
            externalTypingIndicatorPlayers.remove(target.uuid)
        }
        syncBubbleChatState(target)
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
        for (viewer in translationViewers(source)) {
            if (viewer.hasDisconnected()) {
                continue
            }

            if (hasVoiceChat && UTVoiceChatCompat.isPlayerDeafened(viewer) && viewer != source) {
                continue
            }

            val targetLanguage = playerLanguages[viewer.uuid] ?: sourceLanguage
            val viewerAppearance = if (viewer.uuid == source.uuid) {
                bubbleAppearance
            } else {
                bubbleAppearance.copy(visible = true)
            }

            proxy.sendPacketServer(
                viewer,
                SendChatBubblePayload(
                    source.uuid,
                    sourceLanguage,
                    targetLanguage,
                    index,
                    updateTime,
                    viewerAppearance,
                    bubbleVoiceFor(source, viewer),
                    text
                )
            )
        }
    }

    private fun broadcastTypingBubble(source: ServerPlayer, active: Boolean, appearance: SpeechBubbleAppearance) {
        for (viewer in translationViewers(source)) {
            if (viewer.hasDisconnected()) {
                continue
            }

            if (hasVoiceChat && UTVoiceChatCompat.isPlayerDeafened(viewer) && viewer != source) {
                continue
            }

            proxy.sendPacketServer(
                viewer,
                TypingBubbleBroadcastPayload(
                    source.uuid,
                    active,
                    appearance
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

    private fun translationViewers(source: ServerPlayer): List<ServerPlayer> {
        if (!hasVoiceChat) {
            return connectedPlayers(source)
        }

        val fallbackRangeSquared = FALLBACK_BUBBLE_RANGE * FALLBACK_BUBBLE_RANGE
        val viewers = linkedSetOf<ServerPlayer>()
        viewers.add(source)
        viewers.addAll(runCatching { UTVoiceChatCompat.getNearbyPlayers(source) }.getOrDefault(emptyList()))
        viewers.addAll(
            connectedPlayers(source).filter { player ->
                player == source || (
                    player.serverLevel() == source.serverLevel() &&
                        player.distanceToSqr(source) <= fallbackRangeSquared
                    )
            }
        )

        return viewers.filterNot(ServerPlayer::hasDisconnected)
    }

    private fun connectedPlayers(source: ServerPlayer): List<ServerPlayer> {
        return source.server.playerList.players.filterNot(ServerPlayer::hasDisconnected)
    }

    private fun syncBubbleTts(player: ServerPlayer, enabled: Boolean) {
        proxy.sendPacketServer(player, BubbleTtsStatePayload(enabled))
    }

    private fun syncBubbleChatState(player: ServerPlayer) {
        proxy.sendPacketServer(
            player,
            BubbleChatStatePayload(bubbleChatPlayers.contains(player.uuid) || externalTypingIndicatorPlayers.contains(player.uuid))
        )
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

    private const val TRANSCRIPT_BOX_RANGE = 20.0
    private const val TRANSCRIPT_BOX_RANGE_SQR = TRANSCRIPT_BOX_RANGE * TRANSCRIPT_BOX_RANGE

    private fun <T : CustomPacketPayload> registerReceiver(
        type: TypeAndCodec<RegistryFriendlyByteBuf, T>,
        receiver: NetworkManager.NetworkReceiver<T>
    ) {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, type.type, type.codec, receiver)
    }

    private fun registerServerToClientPayloads() {
        registerServerToClientPayload(PacketIds.SERVER_SUPPORT)
        registerServerToClientPayload(PacketIds.SEND_TRANSCRIPT_TO_CLIENT)
        registerServerToClientPayload(PacketIds.SEND_CHAT_BUBBLE)
        registerServerToClientPayload(PacketIds.MARK_INCOMPLETE)
        registerServerToClientPayload(PacketIds.BUBBLE_TTS_STATE)
        registerServerToClientPayload(PacketIds.BUBBLE_CHAT_STATE)
        registerServerToClientPayload(PacketIds.TYPING_BUBBLE_CLIENT)
        registerServerToClientPayload(PacketIds.ADMIN_OPEN_BROWSER)
        registerServerToClientPayload(PacketIds.ADMIN_OPEN_BUBBLE_CUSTOMIZATION)
        registerServerToClientPayload(PacketIds.ADMIN_SET_SPOKEN_LANGUAGE)
        registerServerToClientPayload(PacketIds.FSB_STATE)
    }

    private fun <T : CustomPacketPayload> registerServerToClientPayload(type: TypeAndCodec<RegistryFriendlyByteBuf, T>) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, type.type, type.codec) { _, _ -> }
    }
}
