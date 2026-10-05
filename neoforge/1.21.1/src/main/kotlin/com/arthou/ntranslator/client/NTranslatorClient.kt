package com.arthou.ntranslator.client

import com.mojang.blaze3d.platform.InputConstants
import dev.architectury.event.events.client.ClientGuiEvent
import dev.architectury.event.events.client.ClientLifecycleEvent
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.minecraft.Util
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.bubbles.SpeechBubbleManager
import com.arthou.ntranslator.client.bubbles.ChatTypingBubbleWatcher
import com.arthou.ntranslator.client.books.BookTranslationManager
import com.arthou.ntranslator.client.signs.SignTranslationManager
import com.arthou.ntranslator.client.tts.LocalTtsSpeaker
import com.arthou.ntranslator.client.gui.*
import com.arthou.ntranslator.client.transcribers.SpeechTranscriber
import com.arthou.ntranslator.client.transcribers.windows.sapi5.WindowsSpeechApiTranscriber
import com.arthou.ntranslator.commands.NTranslatorClientCommands
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.events.TranscriptEvents
import com.arthou.ntranslator.fsb.FSBReplacer
import com.arthou.ntranslator.network.PacketIds
import com.arthou.ntranslator.network.UTClientNetworking
import com.arthou.ntranslator.transcript.Transcript
import com.arthou.ntranslator.network.payloads.SendTranscriptToServerPayload
import com.arthou.ntranslator.translator.LocalLibreTranslateInstance
import com.arthou.ntranslator.translator.TranslatorManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

class NTranslatorClient {
    init {
        WindowsSpeechApiTranscriber.isSupported() // runs a check to load Windows Speech API. why write the code again anyway?
        NTranslatorClientCommands.init()

        transcriber = NTranslator.config.client.transcriber.creator.invoke(NTranslator.config.client.spokenLanguage)
        setupTranscriber(transcriber)

        ClientGuiEvent.RENDER_HUD.register { guiGraphics, delta ->
            if (shouldRenderBoxes && NTranslator.config.client.enabled) {
                for (languageBox in languageBoxes) {
                     languageBox.render(guiGraphics, delta.realtimeDeltaTicks)
                }
            }
        }

        ClientTickEvent.CLIENT_POST.register { mc ->
            forceMenuBackgroundBlurOff(mc)

            if (CONFIGURE_BOXES.consumeClick()) {
                mc.setScreen(EditTranscriptBoxesScreen(languageBoxes))
            }

            if (TOGGLE_TRANSCRIPTION.consumeClick()) {
                shouldTranscribe = !shouldTranscribe
                mc.player?.displayClientMessage(
                    Component.translatable("ntranslator.transcript")
                        .append(": ")
                        .append(if (shouldTranscribe) CommonComponents.OPTION_ON else CommonComponents.OPTION_OFF), true
                )
            }

            if (TOGGLE_BOXES.consumeClick() && mc.screen !is EditTranscriptBoxesScreen) {
                shouldRenderBoxes = !shouldRenderBoxes
                NTranslator.config.client.showTranscriptBoxes = shouldRenderBoxes
                NTranslator.saveConfig()
                mc.player?.displayClientMessage(
                    Component.translatable("ntranslator.transcript_boxes")
                        .append(": ")
                        .append(if (shouldRenderBoxes) CommonComponents.OPTION_ON else CommonComponents.OPTION_OFF),
                    true
                )
            }

            if (SET_SPOKEN_LANGUAGE.consumeClick() && mc.screen == null) {
                mc.setScreen(LanguageSelectScreen(null, LanguageSelectScreen.Mode.SPOKEN))
            }

            if (CLEAR_TRANSCRIPTS.consumeClick()) {
                for (box in languageBoxes) {
                    box.transcripts.clear()
                }
            }

            if (OPEN_CONFIG_GUI.consumeClick()) {
                mc.setScreen(UTConfigScreen(null))
            }

            /*if (TRANSLATE_SIGN.consumeClick()) {
                if (mc.player != null && mc.level != null) {
                    val hitResult = mc.player?.pick(7.5, mc.frameTime, false)

                    if (hitResult != null && hitResult is BlockHitResult) {
                        val buf = NTranslator.instance.proxy.createByteBuf()
                        buf.writeBlockPos(hitResult.blockPos)

                        NTranslator.instance.proxy.sendPacketClient(PacketIds.TRANSLATE_SIGN, buf)
                    }
                }
            }*/

            ChatTypingBubbleWatcher.tick(mc)
            SpeechBubbleManager.tick()
            BookTranslationManager.tick(mc)
            SignTranslationManager.tick()
            MonliscStartupScreen.maybeOpen(mc)
        }

        ClientLifecycleEvent.CLIENT_STOPPING.register {
            LocalLibreTranslateInstance.killOpenInstances()
        }

        UTClientNetworking.init()
    }

    private fun forceMenuBackgroundBlurOff(mc: Minecraft) {
        val blur = mc.options.menuBackgroundBlurriness()
        if (blur.get() != 0) {
            blur.set(0)
        }
    }

    fun setupTranscriber(transcriber: SpeechTranscriber) {
        transcriber.updater = SpeechTranscriber.TranscriptUpdater { index, text, isFinal ->
            if (!shouldTranscribe)
                return@TranscriptUpdater

            val updateTime = System.currentTimeMillis()
            val resolvedText = if (!connectedServerHasSupport || UTClientNetworking.serverFsbEnabled) {
                FSBReplacer.apply(text, transcriber.language)
            } else {
                text
            }
            val bubbleAppearance = SpeechBubbleAppearance.fromConfig(NTranslator.config.client.speechBubbles)
            if (connectedServerHasSupport) {
                NTranslator.instance.proxy.sendPacketClient(
                    SendTranscriptToServerPayload(
                        transcriber.language,
                        resolvedText,
                        index,
                        updateTime,
                        bubbleAppearance,
                        isFinal
                    )
                )

                languageBoxes.firstOrNull { it.language == transcriber.language }?.updateTranscript(Minecraft.getInstance().player!!, resolvedText, transcriber.language, index, updateTime, false)

                if (NTranslator.config.client.speechBubbles.showOwnBubble && transcriber.language == SpeechBubbleManager.targetLanguage()) {
                    SpeechBubbleManager.update(Minecraft.getInstance().player!!, resolvedText, transcriber.language, index, updateTime, false, bubbleAppearance)
                }

                if (languageBoxes.none { it.language == transcriber.language }) {
                    TranscriptEvents.UPDATE.invoker().onTranscriptUpdate(Transcript(index, Minecraft.getInstance().player!!, resolvedText, transcriber.language, updateTime, false), transcriber.language)
                }
            } else {
                val useLibreTranslate = NTranslator.config.server.useLibreTranslate

                if (Minecraft.getInstance().player == null)
                    return@TranscriptUpdater

                if (!useLibreTranslate) {
                    handleGoogleTranslationLocally(transcriber.language, resolvedText, index, updateTime, isFinal, bubbleAppearance)
                    return@TranscriptUpdater
                }

                val bubbleTarget = SpeechBubbleManager.targetLanguage()
                val bubbleEnabled = NTranslator.config.client.speechBubbles.showOwnBubble

                if (bubbleEnabled && transcriber.language == bubbleTarget) {
                    SpeechBubbleManager.update(Minecraft.getInstance().player!!, resolvedText, transcriber.language, index, updateTime, false, bubbleAppearance)
                }

                for (box in languageBoxes) {
                    if (box.language == transcriber.language) {
                        box.updateTranscript(Minecraft.getInstance().player!!, resolvedText, transcriber.language, index, updateTime, false)

                        continue
                    }

                    rememberTranslationRequest(Minecraft.getInstance().player!!.uuid, transcriber.language, box.language, index, updateTime)
                    TranslatorManager.queueTranslation(resolvedText, transcriber.language, box.language, Minecraft.getInstance().player!!, index)
                        .whenCompleteAsync { it, e ->
                            if (e != null)
                                return@whenCompleteAsync

                            if (!isCurrentTranslationRequest(Minecraft.getInstance().player!!.uuid, transcriber.language, box.language, index, updateTime))
                                return@whenCompleteAsync

                            box.updateTranscript(Minecraft.getInstance().player!!, it, transcriber.language, index, updateTime, false)

                            if (bubbleEnabled && box.language == bubbleTarget) {
                                SpeechBubbleManager.update(Minecraft.getInstance().player!!, it, transcriber.language, index, updateTime, false, bubbleAppearance)
                            }
                        }
                }

                if (bubbleEnabled && transcriber.language != bubbleTarget && languageBoxes.none { it.language == bubbleTarget }) {
                    rememberTranslationRequest(Minecraft.getInstance().player!!.uuid, transcriber.language, bubbleTarget, index, updateTime)
                    TranslatorManager.queueTranslation(resolvedText, transcriber.language, bubbleTarget, Minecraft.getInstance().player!!, index)
                        .whenCompleteAsync { translated, error ->
                            if (error != null)
                                return@whenCompleteAsync

                            if (!isCurrentTranslationRequest(Minecraft.getInstance().player!!.uuid, transcriber.language, bubbleTarget, index, updateTime))
                                return@whenCompleteAsync

                            SpeechBubbleManager.update(Minecraft.getInstance().player!!, translated, transcriber.language, index, updateTime, false, bubbleAppearance)
                        }
                }

                if (languageBoxes.none { it.language == transcriber.language }) {
                    TranscriptEvents.UPDATE.invoker().onTranscriptUpdate(Transcript(index, Minecraft.getInstance().player!!, resolvedText, transcriber.language, updateTime, false), transcriber.language)
                }
            }
        }
    }

    private fun handleGoogleTranslationLocally(
        sourceLanguage: com.arthou.ntranslator.Language,
        text: String,
        index: Int,
        updateTime: Long,
        isFinal: Boolean,
        bubbleAppearance: SpeechBubbleAppearance
    ) {
        val player = Minecraft.getInstance().player ?: return
        val bubbleTarget = SpeechBubbleManager.targetLanguage()
        val bubbleEnabled = NTranslator.config.client.speechBubbles.showOwnBubble
        val placeholder = "..."

        languageBoxes.firstOrNull { it.language == sourceLanguage }
            ?.updateTranscript(player, text, sourceLanguage, index, updateTime, !isFinal)

        if (bubbleEnabled && bubbleTarget == sourceLanguage) {
            SpeechBubbleManager.update(player, text, sourceLanguage, index, updateTime, !isFinal, bubbleAppearance)
        }

        if (!isFinal) {
            for (box in languageBoxes) {
                if (box.language == sourceLanguage) {
                    continue
                }
                box.updateTranscript(player, placeholder, sourceLanguage, index, updateTime, true)
            }

            if (bubbleEnabled && bubbleTarget != sourceLanguage) {
                SpeechBubbleManager.update(player, placeholder, sourceLanguage, index, updateTime, true, bubbleAppearance)
            }
            return
        }

        for (box in languageBoxes) {
            if (box.language == sourceLanguage) {
                continue
            }

            box.updateTranscript(player, placeholder, sourceLanguage, index, updateTime, true)
            TranslatorManager.queueTranslation(text, sourceLanguage, box.language, player, index)
                .whenCompleteAsync { translated, error ->
                    if (error != null)
                        return@whenCompleteAsync

                    box.updateTranscript(player, translated ?: text, sourceLanguage, index, updateTime, false)

                    if (bubbleEnabled && box.language == bubbleTarget) {
                        SpeechBubbleManager.update(player, translated ?: text, sourceLanguage, index, updateTime, false, bubbleAppearance)
                    }
                }
        }

        if (bubbleEnabled && bubbleTarget != sourceLanguage && languageBoxes.none { it.language == bubbleTarget }) {
            SpeechBubbleManager.update(player, placeholder, sourceLanguage, index, updateTime, true, bubbleAppearance)
            TranslatorManager.queueTranslation(text, sourceLanguage, bubbleTarget, player, index)
                .whenCompleteAsync { translated, error ->
                    if (error != null)
                        return@whenCompleteAsync

                    SpeechBubbleManager.update(player, translated ?: text, sourceLanguage, index, updateTime, false, bubbleAppearance)
                }
        }

        if (languageBoxes.none { it.language == sourceLanguage }) {
            TranscriptEvents.UPDATE.invoker().onTranscriptUpdate(Transcript(index, player, text, sourceLanguage, updateTime, false), sourceLanguage)
        }
    }

    companion object {
        lateinit var transcriber: SpeechTranscriber
        private val latestTranslationRequests = ConcurrentHashMap<LocalTranslationKey, Long>()

        var connectedServerHasSupport = false

        var shouldTranscribe = true
            set(value) {
                field = value
                transcriber.setMuted(!value)
            }

        var shouldRenderBoxes = NTranslator.config.client.showTranscriptBoxes

        val languageBoxes: MutableList<TranscriptBox>
            get() {
                return NTranslator.config.client.transcriptBoxes
            }

        private fun rememberTranslationRequest(playerId: UUID, from: com.arthou.ntranslator.Language, to: com.arthou.ntranslator.Language, index: Int, updateTime: Long) {
            latestTranslationRequests[LocalTranslationKey(playerId, from, to, index)] = updateTime
        }

        private fun isCurrentTranslationRequest(playerId: UUID, from: com.arthou.ntranslator.Language, to: com.arthou.ntranslator.Language, index: Int, updateTime: Long): Boolean {
            return latestTranslationRequests[LocalTranslationKey(playerId, from, to, index)] == updateTime
        }

        private data class LocalTranslationKey(
            val playerId: UUID,
            val from: com.arthou.ntranslator.Language,
            val to: com.arthou.ntranslator.Language,
            val index: Int
        )

        val CONFIGURE_BOXES = (KeyMapping("ntranslator.configure_boxes", -1, "NTranslator"))
        val TOGGLE_TRANSCRIPTION = (KeyMapping("ntranslator.toggle_transcription", -1, "NTranslator"))
        val TOGGLE_BOXES = (KeyMapping("ntranslator.toggle_boxes", -1, "NTranslator"))
        val SET_SPOKEN_LANGUAGE = (KeyMapping("ntranslator.set_spoken_language", -1, "NTranslator"))
        val CLEAR_TRANSCRIPTS = (KeyMapping("ntranslator.clear_transcripts", -1, "NTranslator"))
        //val TRANSLATE_SIGN = (KeyMapping("ntranslator.translate_sign", InputConstants.KEY_F8, "NTranslator"))
        val OPEN_CONFIG_GUI = (KeyMapping("ntranslator.open_config", InputConstants.KEY_F7, "NTranslator"))

        @JvmStatic
        fun registerKeys() {
            KeyMappingRegistry.register(CONFIGURE_BOXES)
            KeyMappingRegistry.register(TOGGLE_TRANSCRIPTION)
            KeyMappingRegistry.register(TOGGLE_BOXES)
            KeyMappingRegistry.register(SET_SPOKEN_LANGUAGE)
            KeyMappingRegistry.register(CLEAR_TRANSCRIPTS)
            //KeyMappingRegistry.register(TRANSLATE_SIGN)
            KeyMappingRegistry.register(OPEN_CONFIG_GUI)
        }

        const val AUTHOR_URL = "https://www.curseforge.com/members/arthou/projects"

        fun displayMessage(component: Component, isError: Boolean = false) {
            val full = Component.empty()
                .append(Component.literal("[NTranslator]: ")
                    .withStyle(if (isError) ChatFormatting.RED else ChatFormatting.YELLOW, ChatFormatting.BOLD)
                )
                .append(component)

            Minecraft.getInstance().gui.chat.addMessage(full)
        }

        fun renderCreditText(guiGraphics: GuiGraphics) {
            val version = NTranslator.instance.proxy.modVersion
            val font = Minecraft.getInstance().font

            guiGraphics.drawString(font, "NTranslator $version", 2, Minecraft.getInstance().window.guiScaledHeight - (font.lineHeight * 2) - 4, 0xAAAAAA)
            guiGraphics.drawString(font, Component.translatable("ntranslator.credit.author"), 2, Minecraft.getInstance().window.guiScaledHeight - font.lineHeight - 2, 0x7EB8FF)
        }

        fun handleCreditClick(mouseX: Double, mouseY: Double): Boolean {
            val mc = Minecraft.getInstance()
            val font = mc.font
            val text = Component.translatable("ntranslator.credit.author").string
            val x = 2
            val y = mc.window.guiScaledHeight - font.lineHeight - 2
            val width = font.width(text)

            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + font.lineHeight) {
                Util.getPlatform().openUri(AUTHOR_URL)
                return true
            }

            return false
        }

        private val queuedForJoin = ConcurrentLinkedQueue<Consumer<Minecraft>>()

        init {
            ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { _ ->
                for (consumer in queuedForJoin) {
                    consumer.accept(Minecraft.getInstance())
                }
                queuedForJoin.clear()
            }
        }

        fun openDownloadRequest() {
            queuedForJoin.add { mc ->
                if (mc.screen is OpenBrowserScreen) {
                    mc.execute {
                        mc.setScreen(RequestDownloadScreen().apply {
                            parent = mc.screen
                        })
                    }
                } else {
                    mc.execute {
                        mc.setScreen(RequestDownloadScreen())
                    }
                }
            }
        }

        fun speakBubbleText(text: String, language: com.arthou.ntranslator.Language, voice: BubbleVoice) {
            if (voice == BubbleVoice.OFF || text.isBlank())
                return

            LocalTtsSpeaker.speak(text, language)
        }

        fun stopBubbleSpeech() {
            LocalTtsSpeaker.stop()
        }
    }
}
