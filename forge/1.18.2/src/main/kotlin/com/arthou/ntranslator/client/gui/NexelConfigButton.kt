package com.arthou.ntranslator.client.gui

import com.arthou.ntranslator.NTranslator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class NexelConfigButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    onPress: OnPress
) : Button(x, y, width, height, message, onPress) {

    override fun renderButton(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val texture = when {
            !active -> BUTTON_DISABLED
            isHoveredOrFocused -> BUTTON_HIGHLIGHTED
            else -> BUTTON
        }

        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.setShaderTexture(0, texture)
        RenderSystem.enableBlend()

        poseStack.pushPose()
        poseStack.translate(x.toDouble(), y.toDouble(), 0.0)
        poseStack.scale(width / BUTTON_TEXTURE_WIDTH.toFloat(), height / BUTTON_TEXTURE_HEIGHT.toFloat(), 1.0f)
        blit(poseStack, 0, 0, 0.0f, 0.0f, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT)
        poseStack.popPose()

        RenderSystem.disableBlend()

        val font = Minecraft.getInstance().font
        drawCenteredString(poseStack, font, message, x + width / 2, y + (height - font.lineHeight) / 2, textColor())
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
    private var buttonWidth: Int = 150
    private var buttonHeight: Int = 20

    fun bounds(x: Int, y: Int, width: Int, height: Int): NexelConfigButtonBuilder {
        buttonX = x
        buttonY = y
        buttonWidth = width
        buttonHeight = height
        return this
    }

    fun build(): Button {
        return NexelConfigButton(buttonX, buttonY, buttonWidth, buttonHeight, message, onPress)
    }
}

fun nexelConfigButtonBuilder(message: Component, onPress: Button.OnPress): NexelConfigButtonBuilder {
    return NexelConfigButtonBuilder(message, onPress)
}
