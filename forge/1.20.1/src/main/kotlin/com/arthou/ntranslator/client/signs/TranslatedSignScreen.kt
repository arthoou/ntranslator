package com.arthou.ntranslator.client.signs

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.gui.LanguageSelectScreen
import com.arthou.ntranslator.client.translation.ClientTranslationHelper
import com.arthou.ntranslator.client.tts.LocalTtsSpeaker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max

class TranslatedSignScreen(
    private val translatedTitle: Component,
    private var translatedLines: List<String>,
    private var sourceInfo: Component,
    private var targetLanguage: Language = NTranslator.config.client.subtitleLanguage,
    private val sourceLines: List<String> = translatedLines
) : Screen(Component.translatable("ntranslator.sign.title")) {
    private var wrappedLines: List<FormattedCharSequence> = emptyList()
    private var ttsButton: Button? = null
    private var languageButton: Button? = null
    private var refreshId = 0

    override fun init() {
        wrappedLines = wrapLines()
        val sideX = (panelRight() + 4).coerceAtMost(width - 24)
        val ttsY = contentBottom() - 24

        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) {
                onClose()
            }.bounds(width / 2 - 50, contentBottom() + 12, 100, 20).build()
        )
        languageButton = Button.builder(Component.literal("艾")) {
            Minecraft.getInstance().setScreen(LanguageSelectScreen.openSubtitles(this) {
                refreshTranslation()
            })
        }.bounds(sideX, ttsY - 22, 20, 20).build().also {
            it.tooltip = net.minecraft.client.gui.components.Tooltip.create(Component.translatable("ntranslator.sign.language"))
            addRenderableWidget(it)
        }
        ttsButton = Button.builder(Component.empty()) {
            speakTranslatedText()
        }.bounds(sideX, ttsY, 20, 20).build().also {
            it.tooltip = net.minecraft.client.gui.components.Tooltip.create(Component.translatable("ntranslator.sign.tts"))
            addRenderableWidget(it)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics)

        val font = Minecraft.getInstance().font
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        val left = width / 2 - panelWidth / 2
        val top = height / 2 - panelHeight / 2

        guiGraphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE0222A36.toInt())
        guiGraphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF5D738F.toInt())

        guiGraphics.drawCenteredString(font, translatedTitle, width / 2, top + 10, 0xE7F0FF)
        guiGraphics.drawCenteredString(font, sourceInfo, width / 2, top + 24, 0x8FD2FF)

        wrappedLines.forEachIndexed { index, line ->
            guiGraphics.drawCenteredString(font, line, width / 2, top + 40 + (index * 10), 0xFFFFFF)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        ttsButton?.let {
            guiGraphics.blit(SPEAKER_ICON, it.x + 2, it.y + 2, 0f, 0f, 16, 16, 16, 16)
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics) {
        guiGraphics.fill(0, 0, width, height, 0xD010141B.toInt())
    }

    private fun wrapLines(): List<FormattedCharSequence> {
        val font = Minecraft.getInstance().font
        val maxLineWidth = max(180, (width * 0.45).toInt()).coerceAtMost(320) - 30
        return translatedLines
            .flatMap { line ->
                val split = font.split(Component.literal(line), maxLineWidth)
                if (split.isEmpty()) listOf(font.split(Component.literal(" "), 1).first()) else split
            }
    }

    private fun panelWidth(): Int {
        val font = Minecraft.getInstance().font
        val widest = wrappedLines.maxOfOrNull { font.width(it) } ?: 0
        return max(180, widest + 30).coerceAtMost(350)
    }

    private fun panelHeight(): Int {
        return max(86, 50 + (wrappedLines.size * 10) + 12)
    }

    private fun contentBottom(): Int {
        return (height / 2 - panelHeight() / 2) + panelHeight()
    }

    private fun panelRight(): Int {
        return (width / 2 - panelWidth() / 2) + panelWidth()
    }

    private fun speakTranslatedText() {
        val text = translatedLines
            .map(String::trim)
            .filter { it.isNotBlank() && it != "..." }
            .joinToString(" ")

        if (text.isBlank()) {
            return
        }

        LocalTtsSpeaker.speak(text, targetLanguage)
    }

    private fun refreshTranslation() {
        val original = sourceLines
            .map(String::trim)
            .filter { it.isNotBlank() }

        if (original.isEmpty()) {
            return
        }

        val requestId = ++refreshId
        val target = NTranslator.config.client.subtitleLanguage
        targetLanguage = target
        translatedLines = listOf("...")
        sourceInfo = Component.translatable("ntranslator.sign.loading.subtitle")
        wrappedLines = wrapLines()

        ClientTranslationHelper.translateLinesAsync(original, target).whenComplete { result, error ->
            if (error != null || result == null) {
                return@whenComplete
            }

            Minecraft.getInstance().execute {
                if (Minecraft.getInstance().screen !== this || requestId != refreshId) {
                    return@execute
                }

                targetLanguage = target
                translatedLines = result.lines
                sourceInfo = Component.literal("${result.sourceLanguage.displayCode} -> ${target.displayCode}")
                wrappedLines = wrapLines()
            }
        }
    }

    companion object {
        private val SPEAKER_ICON: ResourceLocation = NTranslator.id("textures/gui/speaker.png")
    }
}
