package com.arthou.ntranslator.client.gui

import com.arthou.ntranslator.NTranslator
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

object NTranslatorCredits {
    const val AUTHOR_URL = "https://www.curseforge.com/members/arthou/projects"

    fun render(guiGraphics: GuiGraphics) {
        val mc = Minecraft.getInstance()
        val font = mc.font
        val version = NTranslator.instance.proxy.modVersion

        guiGraphics.drawString(
            font,
            "NTranslator $version",
            2,
            mc.window.guiScaledHeight - (font.lineHeight * 2) - 4,
            0xAAAAAA
        )
        guiGraphics.drawString(
            font,
            Component.translatable("ntranslator.credit.author"),
            2,
            mc.window.guiScaledHeight - font.lineHeight - 2,
            0x7EB8FF
        )
    }

    fun handleClick(mouseX: Double, mouseY: Double): Boolean {
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
}

