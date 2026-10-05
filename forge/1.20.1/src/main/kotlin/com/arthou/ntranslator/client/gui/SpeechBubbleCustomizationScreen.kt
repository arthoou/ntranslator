package com.arthou.ntranslator.client.gui

import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.client.bubbles.SpeechBubbleManager
import com.arthou.ntranslator.client.bubbles.SpeechBubbleRenderer
import com.arthou.ntranslator.client.transcribers.browser.BrowserSpeechTranscriber
import com.arthou.ntranslator.config.SpeechBubbleStyle
import com.arthou.ntranslator.config.SpeechBubbleFont
import com.arthou.ntranslator.config.SpeechBubbleLineMode
import com.arthou.ntranslator.config.SpeechBubbleModeRules
import com.arthou.ntranslator.network.UTClientNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class SpeechBubbleCustomizationScreen(private val parent: Screen?) : Screen(Component.translatable("gui.ntranslator.config.speech_bubble")) {
    private val config
        get() = NTranslator.config.client.speechBubbles

    private val palette = listOf(
        0x000000, 0x1D2B53, 0x2E8B57, 0x00A8E8, 0x64E6FF, 0x6C63FF,
        0xC218D5, 0xFF78F3, 0xD62839, 0xFF6474, 0xFF7A1A, 0xFFB000,
        0xFFF070, 0x64FF6A, 0x0B3D2E, 0x7A4A22, 0xC68642, 0xF2D3A0,
        0x2B2D42, 0x5C677D, 0xA0A0A0, 0xF5F5F5, 0xFFF8D6, 0xFFFFFF
    )

    override fun init() {
        if (config.style == SpeechBubbleStyle.CIRCULAR) {
            config.style = SpeechBubbleStyle.ROUNDED
        }
        config.lineMode = config.lineMode.resolved()
        config.font = config.font.resolved()

        val layout = screenLayout()
        val toggleWidth = (layout.contentWidth - 12) / 2
        val forceSingleLine = false

        addRenderableWidget(
            Button.builder(toggleMessage("gui.ntranslator.config.speech_bubble.enabled", config.enabled)) {
                config.enabled = !config.enabled
                it.message = toggleMessage("gui.ntranslator.config.speech_bubble.enabled", config.enabled)
            }
                .pos(layout.contentLeft, layout.toggleY)
                .size(toggleWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(toggleMessage("gui.ntranslator.config.speech_bubble.show_own", config.showOwnBubble)) {
                config.showOwnBubble = !config.showOwnBubble
                it.message = toggleMessage("gui.ntranslator.config.speech_bubble.show_own", config.showOwnBubble)
            }
                .pos(layout.contentLeft + toggleWidth + 12, layout.toggleY)
                .size(toggleWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(styleMessage()) {
                config.style = nextStyle(currentStyle())
                it.message = styleMessage()
            }
                .pos(layout.contentLeft, layout.styleButtonY)
                .size(layout.contentWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(fontMessage()) {
                config.font = nextFont(config.font)
                it.message = fontMessage()
            }
                .pos(layout.contentLeft, layout.fontButtonY)
                .size(layout.contentWidth, 20)
                .build()
        )

        if (!forceSingleLine) {
            addRenderableWidget(
                Button.builder(lineModeMessage(SpeechBubbleLineMode.STACKED)) {
                    config.lineMode = SpeechBubbleLineMode.STACKED
                    rebuildWidgets()
                }
                    .pos(layout.contentLeft, layout.lineModeButtonY)
                    .size(toggleWidth, 20)
                    .build()
            )

            addRenderableWidget(
                Button.builder(lineModeMessage(SpeechBubbleLineMode.SINGLE_LINE)) {
                    config.lineMode = SpeechBubbleLineMode.SINGLE_LINE
                    rebuildWidgets()
                }
                    .pos(layout.contentLeft + toggleWidth + 12, layout.lineModeButtonY)
                    .size(toggleWidth, 20)
                    .build()
            )
        }

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) {
                onClose()
            }
                .pos(layout.contentLeft, layout.backButtonY)
                .size(layout.contentWidth, 20)
                .build()
        )

        addColorRow(layout, layout.borderSwatchY, { config.borderColor }, { config.borderColor = it })
        addColorRow(layout, layout.fillSwatchY, { config.fillColor }, { config.fillColor = it })
        addColorRow(layout, layout.textSwatchY, { config.textColor }, { config.textColor = it })
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val layout = screenLayout()

        guiGraphics.fill(0, 0, width, height, 0x7A101622)

        guiGraphics.fill(layout.panelLeft - 4, layout.panelTop - 4, layout.panelLeft + layout.panelWidth + 4, layout.panelTop + layout.panelHeight + 4, 0x66242F3F)
        guiGraphics.fill(layout.panelLeft, layout.panelTop, layout.panelLeft + layout.panelWidth, layout.panelTop + layout.panelHeight, 0xE0202B38.toInt())
        guiGraphics.fill(layout.panelLeft, layout.panelTop, layout.panelLeft + layout.panelWidth, layout.panelTop + 28, 0xFF31445C.toInt())
        guiGraphics.fill(layout.contentLeft - 4, layout.panelTop + 40, layout.contentRight + 4, layout.panelTop + 166, 0x7A323E4E)
        guiGraphics.fill(layout.contentLeft - 4, layout.panelTop + 171, layout.contentRight + 4, layout.panelTop + 330, 0x6A323E4E)

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawString(font, title, layout.contentLeft, layout.panelTop + 10, 0xFFFFFF, false)
        guiGraphics.drawCenteredString(font, Component.translatable("gui.ntranslator.config.speech_bubble.preview"), layout.centerX, layout.previewLabelY, 0xE7F0FF)

        SpeechBubbleRenderer.renderPreview(guiGraphics, layout.centerX, layout.previewBubbleY, Component.translatable("gui.ntranslator.config.speech_bubble.sample").string)
        guiGraphics.drawCenteredString(font, Minecraft.getInstance().user.name, layout.centerX, layout.previewNameY, 0xF5F9FF)

        guiGraphics.drawString(font, Component.translatable("gui.ntranslator.config.speech_bubble.border"), layout.contentLeft, layout.borderLabelY, 0xE7F0FF, false)
        guiGraphics.drawString(font, Component.translatable("gui.ntranslator.config.speech_bubble.fill"), layout.contentLeft, layout.fillLabelY, 0xE7F0FF, false)
        guiGraphics.drawString(font, Component.translatable("gui.ntranslator.config.speech_bubble.text"), layout.contentLeft, layout.textLabelY, 0xE7F0FF, false)
        if (!SpeechBubbleModeRules.forceSingleLineForGooglePlaceholder()) {
            guiGraphics.drawString(font, Component.translatable("gui.ntranslator.config.speech_bubble.line_mode"), layout.contentLeft, layout.lineModeLabelY, 0xE7F0FF, false)
        }
   }

    override fun onClose() {
        NTranslator.saveConfig()
        SpeechBubbleManager.clear()
        UTClientNetworking.syncBubbleAppearance()
        UTClientNetworking.syncRequestedLanguages()
        (NTranslatorClient.transcriber as? BrowserSpeechTranscriber)?.syncBubbleAppearance()
        Minecraft.getInstance().setScreen(parent)
    }

    private fun addColorRow(layout: Layout, y: Int, getter: () -> Int, setter: (Int) -> Unit) {
        val swatchBlockWidth = (palette.size * SWATCH_STEP) - SWATCH_GAP
        val baseX = layout.panelLeft + (layout.panelWidth - swatchBlockWidth) / 2
        for ((index, color) in palette.withIndex()) {
            addRenderableWidget(ColorSwatchButton(baseX + (index * SWATCH_STEP), y, color, getter, setter))
        }
    }

    private fun currentStyle(): SpeechBubbleStyle {
        return config.style.resolved()
    }

    private fun nextStyle(current: SpeechBubbleStyle): SpeechBubbleStyle {
        return when (current) {
            SpeechBubbleStyle.SQUARED -> SpeechBubbleStyle.ROUNDED
            SpeechBubbleStyle.ROUNDED,
            SpeechBubbleStyle.CIRCULAR -> SpeechBubbleStyle.SQUARED
        }
    }

    private fun styleMessage(): Component {
        return Component.translatable("gui.ntranslator.config.speech_bubble.style")
            .append(": ")
            .append(Component.translatable("gui.ntranslator.config.speech_bubble.style.${currentStyle().name.lowercase()}"))
    }

    private fun fontMessage(): Component {
        return Component.translatable("gui.ntranslator.config.speech_bubble.font")
            .append(": ")
            .append(Component.translatable("gui.ntranslator.config.speech_bubble.font.${config.font.name.lowercase()}"))
    }

    private fun nextFont(current: SpeechBubbleFont): SpeechBubbleFont {
        val values = SpeechBubbleFont.visible
        return values[(values.indexOf(current) + 1) % values.size]
    }

    private fun lineModeMessage(mode: SpeechBubbleLineMode): Component {
        return Component.literal(if (config.lineMode.resolved() == mode) "☑ " else "☐ ")
            .append(Component.translatable("gui.ntranslator.config.speech_bubble.line_mode.${mode.name.lowercase()}"))
    }

    private fun toggleMessage(key: String, value: Boolean): Component {
        return Component.translatable(key)
            .append(": ")
            .append(Component.translatable("ntranslator.value.$value"))
    }

    private fun screenLayout(): Layout {
        val panelWidth = minOf(430, width - 24)
        val panelHeight = minOf(430, height - 24)
        val panelLeft = (width - panelWidth) / 2
        val panelTop = (height - panelHeight) / 2
        val contentLeft = panelLeft + 18
        val contentRight = panelLeft + panelWidth - 18
        val contentWidth = contentRight - contentLeft

        return Layout(
            panelLeft = panelLeft,
            panelTop = panelTop,
            panelWidth = panelWidth,
            panelHeight = panelHeight,
            contentLeft = contentLeft,
            contentRight = contentRight,
            contentWidth = contentWidth,
            centerX = panelLeft + (panelWidth / 2),
            toggleY = panelTop + 46,
            previewLabelY = panelTop + 80,
            previewBubbleY = panelTop + 112,
            previewNameY = panelTop + 151,
            borderLabelY = panelTop + 180,
            borderSwatchY = panelTop + 193,
            fillLabelY = panelTop + 218,
            fillSwatchY = panelTop + 231,
            textLabelY = panelTop + 256,
            textSwatchY = panelTop + 269,
            styleButtonY = panelTop + 300,
            fontButtonY = panelTop + 324,
            lineModeLabelY = panelTop + 348,
            lineModeButtonY = panelTop + 361,
            backButtonY = panelTop + 386
        )
    }

    private data class Layout(
        val panelLeft: Int,
        val panelTop: Int,
        val panelWidth: Int,
        val panelHeight: Int,
        val contentLeft: Int,
        val contentRight: Int,
        val contentWidth: Int,
        val centerX: Int,
        val toggleY: Int,
        val previewLabelY: Int,
        val previewBubbleY: Int,
        val previewNameY: Int,
        val borderLabelY: Int,
        val borderSwatchY: Int,
        val fillLabelY: Int,
        val fillSwatchY: Int,
        val textLabelY: Int,
        val textSwatchY: Int,
        val styleButtonY: Int,
        val fontButtonY: Int,
        val lineModeLabelY: Int,
        val lineModeButtonY: Int,
        val backButtonY: Int
    )

    private class ColorSwatchButton(
        x: Int,
        y: Int,
        private val color: Int,
        private val getter: () -> Int,
        private val setter: (Int) -> Unit
    ) : AbstractWidget(x, y, SWATCH_SIZE, SWATCH_SIZE, Component.empty()) {
        override fun onClick(mouseX: Double, mouseY: Double) {
            setter(color)
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            guiGraphics.fill(x, y, x + width, y + height, 0xFF090D12.toInt())
            guiGraphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF000000.toInt() or color)

            val outline = when {
                getter() == color -> 0xFFFFD36A.toInt()
                isHovered -> 0x88FFFFFF.toInt()
                else -> 0x55242F3F
            }

            guiGraphics.renderOutline(x, y, width, height, outline)
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        }
    }

    companion object {
        private const val SWATCH_SIZE = 14
        private const val SWATCH_GAP = 2
        private const val SWATCH_STEP = SWATCH_SIZE + SWATCH_GAP
    }
}
