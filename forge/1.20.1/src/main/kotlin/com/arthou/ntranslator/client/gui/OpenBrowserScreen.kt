package com.arthou.ntranslator.client.gui

import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.client.transcribers.browser.bridge.BrowserBridgeLauncher

class OpenBrowserScreen(val address: String) : Screen(Component.empty()) {
    override fun init() {
        super.init()

        addRenderableWidget(
            Button.builder(Component.translatable("ntranslator.do_not_show_again")) {
                openAddress(false)
                NTranslator.config.client.openBrowserWithoutPrompt = true
                NTranslator.saveConfig()
                this.onClose()
            }
                .pos(this.width / 2 - (Button.DEFAULT_WIDTH / 2), this.height - 20 - Button.DEFAULT_HEIGHT - 10 - Button.DEFAULT_HEIGHT - 5 - Button.DEFAULT_HEIGHT - 15)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("ntranslator.open_browser.hide_browser")) {
                openAddress(true)
                this.onClose()
            }
                .pos(this.width / 2 - (Button.DEFAULT_WIDTH / 2), this.height - 20 - Button.DEFAULT_HEIGHT - 5 - Button.DEFAULT_HEIGHT - 15)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("ntranslator.open_browser.open_in_browser")) {
                openAddress(false)
                this.onClose()
            }
                .pos(this.width / 2 - (Button.DEFAULT_WIDTH / 2), this.height - 20 - Button.DEFAULT_HEIGHT - 15)
                .build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xD010141B.toInt())
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val split = this.font.split(FormattedText.of(I18n.get("ntranslator.open_browser.prompt")), this.width / 2)

        val start = (this.height / 2 - (10 * split.size))
        for ((index, text) in split.withIndex()) {
            guiGraphics.drawCenteredString(this.font, text, this.width / 2, start + (this.font.lineHeight * index), 16777215)
        }
    }

    private fun openAddress(hidden: Boolean) {
        if (!hidden) {
            BrowserBridgeLauncher.stop()
            Util.getPlatform().openUri(address)
            return
        }

        if (!NTranslator.config.server.preferEdgeBrowser && !NTranslator.config.server.preferChromeBrowser) {
            NTranslator.config.server.preferEdgeBrowser = true
        }
        NTranslator.saveConfig()
        val browser = if (NTranslator.config.server.preferChromeBrowser && !NTranslator.config.server.preferEdgeBrowser) "chrome" else "edge"
        NTranslator.config.server.lastHiddenBrowser = browser
        val launched = BrowserBridgeLauncher.open(address, browser)
        if (!launched) {
            NTranslatorClient.displayMessage(
                Component.literal("Could not start hidden $browser browser bridge."),
                true
            )
        }
    }
}
