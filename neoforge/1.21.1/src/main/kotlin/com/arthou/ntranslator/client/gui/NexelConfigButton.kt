package com.arthou.ntranslator.client.gui

import com.arthou.ntranslator.NTranslator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class NexelConfigButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    onPress: Button.OnPress
) : Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {
    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val texture = when {
            !active -> BUTTON_DISABLED
            isHovered -> BUTTON_HIGHLIGHTED
            else -> BUTTON
        }

        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toDouble(), y.toDouble(), 0.0)
        pose.scale(width / BUTTON_TEXTURE_WIDTH.toFloat(), height / BUTTON_TEXTURE_HEIGHT.toFloat(), 1.0f)
        guiGraphics.blit(texture, 0, 0, 0.0f, 0.0f, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT)
        pose.popPose()

        val font = Minecraft.getInstance().font
        guiGraphics.drawCenteredString(font, message, x + width / 2, y + (height - font.lineHeight) / 2, textColor())
    }

    private fun textColor(): Int {
        return if (active) 0xFFFFFFFF.toInt() else 0xFFA0A0A0.toInt()
    }

    companion object {
        private const val BUTTON_TEXTURE_WIDTH = 200
        private const val BUTTON_TEXTURE_HEIGHT = 20
        private val BUTTON: ResourceLocation = NTranslator.id("textures/gui/widget/button.png")
        private val BUTTON_HIGHLIGHTED: ResourceLocation = NTranslator.id("textures/gui/widget/button_highlighted.png")
        private val BUTTON_DISABLED: ResourceLocation = NTranslator.id("textures/gui/widget/button_disabled.png")
    }
}

class NexelConfigButtonBuilder(
    private val message: Component,
    private val onPress: Button.OnPress
) {
    private var buttonX: Int = 0
    private var buttonY: Int = 0
    private var buttonWidth: Int = Button.DEFAULT_WIDTH
    private var buttonHeight: Int = Button.DEFAULT_HEIGHT
    private var buttonTooltip: Tooltip? = null

    fun pos(x: Int, y: Int): NexelConfigButtonBuilder {
        buttonX = x
        buttonY = y
        return this
    }

    fun width(width: Int): NexelConfigButtonBuilder {
        buttonWidth = width
        return this
    }

    fun size(width: Int, height: Int): NexelConfigButtonBuilder {
        buttonWidth = width
        buttonHeight = height
        return this
    }

    fun bounds(x: Int, y: Int, width: Int, height: Int): NexelConfigButtonBuilder {
        buttonX = x
        buttonY = y
        buttonWidth = width
        buttonHeight = height
        return this
    }

    fun tooltip(tooltip: Tooltip): NexelConfigButtonBuilder {
        buttonTooltip = tooltip
        return this
    }

    fun build(): Button {
        return NexelConfigButton(buttonX, buttonY, buttonWidth, buttonHeight, message, onPress).apply {
            buttonTooltip?.let { setTooltip(it) }
        }
    }
}

fun nexelConfigButtonBuilder(message: Component, onPress: Button.OnPress): NexelConfigButtonBuilder {
    return NexelConfigButtonBuilder(message, onPress)
}

