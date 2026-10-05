package com.arthou.ntranslator.client.bubbles

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FastColor
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.player.Player
import com.arthou.ntranslator.config.SpeechBubbleLineMode
import com.arthou.ntranslator.config.SpeechBubbleModeRules
import com.arthou.ntranslator.config.SpeechBubbleFont
import com.arthou.ntranslator.config.SpeechBubbleStyle
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

object SpeechBubbleRenderer {
    private const val MIN_BALLOON_WIDTH = 13
    private const val NAME_TAG_CLEARANCE = 0.30
    private const val STACK_GAP = 1f
    private const val TEXTURE_SIZE = 32f
    private const val BUBBLE_VERTICAL_PIXEL_NUDGE = 1.5f

    private val BUBBLE_FONT = NTranslator.id("bubble")
    private val VANILLA_TWEAKS_FONT = NTranslator.id("vanillatweaks")
    private val VANILLA_TWEAKS_ALT_FONT = NTranslator.id("vanillatweaks_alt")
    private val ROUNDED_BORDER = NTranslator.id("textures/gui/speech_bubbles/rounded_border.png")
    private val ROUNDED_FILL = NTranslator.id("textures/gui/speech_bubbles/rounded_fill.png")
    private val SQUARED_BORDER = NTranslator.id("textures/gui/speech_bubbles/squared_border.png")
    private val SQUARED_FILL = NTranslator.id("textures/gui/speech_bubbles/squared_fill.png")

    @JvmStatic
    fun renderPlayer(player: Player, poseStack: PoseStack, packedLight: Int) {
        val client = Minecraft.getInstance()
        val config = NTranslator.config.client.speechBubbles
        if (!config.enabled && player != client.player)
            return

        if (player == client.player && !config.showOwnBubble)
            return

        val states = SpeechBubbleManager.bubblesFor(player.uuid)
        if (states.isEmpty())
            return

        val camera = client.cameraEntity ?: return
        if (camera !== player && camera.distanceToSqr(player) > 4096.0)
            return

        val font = client.font
        val cameraOrientation = client.entityRenderDispatcher.cameraOrientation()

        poseStack.pushPose()
        poseStack.translate(0.0, player.bbHeight + config.heightOffset.toDouble() - 0.4 + (config.padding / 32.0) + NAME_TAG_CLEARANCE, 0.0)
        poseStack.mulPose(cameraOrientation)
        poseStack.scale(-0.025f, -0.025f, 0.025f)
        poseStack.translate(0f, -BUBBLE_VERTICAL_PIXEL_NUDGE, 0f)

        var stackOffset = 0f
        for ((stackIndex, state) in states.withIndex()) {
            val bubbleBaseAlpha = SpeechBubbleManager.alphaFor(state)
            if (bubbleBaseAlpha <= 0.03f)
                continue

            val bubbleAlpha = bubbleBaseAlpha * (if (player.isCrouching()) 0.2f else 0.9f)
            val textAlpha = 1.0f

            val layouts = BubbleLayout.createStack(font, state.text, state.sourceLanguage, state.appearance.lineMode, state.appearance.font, config.maxWidth, config.padding)
            val textures = texturesFor(state.appearance.style)

            for ((layoutIndex, layout) in layouts.asReversed().withIndex()) {
                poseStack.pushPose()
                poseStack.translate(0f, -stackOffset, 0f)

                drawBubble(
                    poseStack.last().pose(),
                    textures,
                    layout,
                    applyAlpha(state.appearance.borderColor, bubbleAlpha),
                    applyAlpha(state.appearance.fillColor, bubbleAlpha),
                    stackIndex == 0 && layoutIndex == 0
                )
                drawTextWorld(
                    poseStack,
                    font,
                    layout,
                    applyAlpha(state.appearance.textColor, textAlpha),
                    packedLight
                )

                poseStack.popPose()
                stackOffset += layout.stackHeight + STACK_GAP
            }
        }

        poseStack.popPose()
    }

    fun renderPreview(guiGraphics: GuiGraphics, centerX: Int, anchorY: Int, text: String) {
        val config = NTranslator.config.client.speechBubbles
        val font = Minecraft.getInstance().font
        val layouts = BubbleLayout.createStack(font, text, NTranslator.config.client.subtitleLanguage, SpeechBubbleModeRules.effectiveLineMode(config), config.font, config.maxWidth, config.padding)
        val textures = texturesFor(config.style.resolved())

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(centerX.toFloat(), anchorY.toFloat(), 20.0f)
        var stackOffset = 0f
        for ((layoutIndex, layout) in layouts.asReversed().withIndex()) {
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(0f, -stackOffset, 0f)
            drawBubbleGui(
                guiGraphics.pose().last().pose(),
                textures,
                layout,
                config.borderColor or -0x1000000,
                config.fillColor or -0x1000000,
                layoutIndex == 0
            )
            drawTextGui(guiGraphics, font, layout, config.textColor or -0x1000000)
            guiGraphics.pose().popPose()
            stackOffset += layout.stackHeight + STACK_GAP
        }
        guiGraphics.pose().popPose()
    }

    private fun drawBubble(matrix: Matrix4f, textures: BubbleTextures, layout: BubbleLayout, borderColor: Int, fillColor: Int, showArrow: Boolean) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.enableDepthTest()
        RenderSystem.disableCull()
        RenderSystem.enablePolygonOffset()
        RenderSystem.polygonOffset(3.0f, 3.0f)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        drawLayer(matrix, textures.border, borderColor, layout, showArrow, true)
        drawLayer(matrix, textures.fill, fillColor, layout, showArrow, false)

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.polygonOffset(0.0f, 0.0f)
        RenderSystem.disablePolygonOffset()
        RenderSystem.enableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
    }

    private fun drawBubbleGui(matrix: Matrix4f, textures: BubbleTextures, layout: BubbleLayout, borderColor: Int, fillColor: Int, showArrow: Boolean) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableDepthTest()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        drawLayer(matrix, textures.border, borderColor, layout, showArrow, true)
        drawLayer(matrix, textures.fill, fillColor, layout, showArrow, false)

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
    }

    private fun drawLayer(matrix: Matrix4f, texture: ResourceLocation, color: Int, layout: BubbleLayout, showArrow: Boolean, cutBottomCenter: Boolean) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        RenderSystem.setShaderTexture(0, texture)

        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)

        val topY = layout.baseY - layout.padding
        val middleY = layout.baseY + 5 - layout.padding
        val bottomY = 5 + layout.padding
        val leftX = -layout.baseX - 3 - layout.padding
        val middleX = -layout.baseX + 2 - layout.padding
        val rightX = layout.baseX - 1 + layout.padding
        val middleWidth = layout.balloonWidth - 4 + (layout.padding * 2)

        // Left
        blit(builder, matrix, leftX, topY, 5, 5, 0f, 0f, 5, 5, color)
        blit(builder, matrix, leftX, middleY, 5, layout.middleHeight, 0f, 6f, 5, 1, color)
        blit(builder, matrix, leftX, bottomY, 5, 5, 0f, 8f, 5, 5, color)

        val gapWidth = if (showArrow && cutBottomCenter) 5 else 0
        val leftBottomWidth = if (gapWidth > 0) (middleWidth - gapWidth) / 2 else middleWidth
        val rightBottomWidth = if (gapWidth > 0) middleWidth - leftBottomWidth - gapWidth else 0

        // Middle
        blit(builder, matrix, middleX, topY, middleWidth, 5, 6f, 0f, 5, 5, color)
        blit(builder, matrix, middleX, middleY, middleWidth, layout.middleHeight, 6f, 6f, 5, 1, color)
        if (gapWidth > 0) {
            blit(builder, matrix, middleX, bottomY, leftBottomWidth, 5, 6f, 8f, 5, 5, color)
            blit(builder, matrix, middleX + leftBottomWidth + gapWidth, bottomY, rightBottomWidth, 5, 6f, 8f, 5, 5, color)
        } else {
            blit(builder, matrix, middleX, bottomY, middleWidth, 5, 6f, 8f, 5, 5, color)
        }

        // Right
        blit(builder, matrix, rightX, topY, 5, 5, 12f, 0f, 5, 5, color)
        blit(builder, matrix, rightX, middleY, 5, layout.middleHeight, 12f, 6f, 5, 1, color)
        blit(builder, matrix, rightX, bottomY, 5, 5, 12f, 8f, 5, 5, color)

        if (showArrow) {
            blit(builder, matrix, -3, 9 + layout.padding, 7, 4, 18f, 6f, 7, 4, color)
        }

        BufferUploader.drawWithShader(builder.end())
    }

    private fun blit(
        builder: BufferBuilder,
        matrix: Matrix4f,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        uOffset: Float,
        vOffset: Float,
        uWidth: Int,
        vHeight: Int,
        color: Int
    ) {
        val x2 = x + width
        val y2 = y + height
        val minU = uOffset / TEXTURE_SIZE
        val maxU = (uOffset + uWidth) / TEXTURE_SIZE
        val minV = vOffset / TEXTURE_SIZE
        val maxV = (vOffset + vHeight) / TEXTURE_SIZE

        vertex(builder, matrix, x.toFloat(), y.toFloat(), minU, minV, color)
        vertex(builder, matrix, x.toFloat(), y2.toFloat(), minU, maxV, color)
        vertex(builder, matrix, x2.toFloat(), y2.toFloat(), maxU, maxV, color)
        vertex(builder, matrix, x2.toFloat(), y.toFloat(), maxU, minV, color)
    }

    private fun vertex(builder: BufferBuilder, matrix: Matrix4f, x: Float, y: Float, u: Float, v: Float, color: Int) {
        builder.vertex(matrix, x, y, 0f)
            .uv(u, v)
            .color(
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color),
                FastColor.ARGB32.alpha(color)
            )
            .endVertex()
    }

    private fun drawTextWorld(poseStack: PoseStack, font: Font, layout: BubbleLayout, textColor: Int, packedLight: Int) {
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        poseStack.pushPose()
        poseStack.translate(0.0, 0.0, 0.25)
        if (layout.placeholder) {
            val placeholder = animatedPlaceholder()
            font.drawInBatch(
                placeholder,
                (-(font.width("...") / 2) + 1).toFloat(),
                layout.textBaseY.toFloat(),
                textColor,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT
            )
            bufferSource.endBatch()
            poseStack.popPose()
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
            return
        }

        var textDistance = 0

        for (line in layout.lines) {
            font.drawInBatch(
                line,
                (-(font.width(line) / 2) + 1).toFloat(),
                (layout.textBaseY + textDistance).toFloat(),
                textColor,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT
            )
            textDistance += font.lineHeight
        }
        bufferSource.endBatch()
        poseStack.popPose()
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
    }

    private fun drawTextGui(guiGraphics: GuiGraphics, font: Font, layout: BubbleLayout, textColor: Int) {
        if (layout.placeholder) {
            guiGraphics.drawString(font, animatedPlaceholder(), -(font.width("...") / 2) + 1, layout.textBaseY, textColor, false)
            return
        }

        var textDistance = 0

        for (line in layout.lines) {
            guiGraphics.drawString(font, line, -(font.width(line) / 2) + 1, layout.textBaseY + textDistance, textColor, false)
            textDistance += font.lineHeight
        }
    }

    private fun applyAlpha(rgb: Int, alpha: Float): Int {
        val clamped = (alpha.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
        return FastColor.ARGB32.color(clamped, (rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
    }

    private fun toBillboardEuler(quaternion: Quaternionf): Vector3f {
        val forward = Vector3f(0f, 0f, 1f).rotate(quaternion)
        val yaw = Math.toDegrees(kotlin.math.atan2(forward.x.toDouble(), forward.z.toDouble())).toFloat()
        val horizontal = kotlin.math.sqrt((forward.x * forward.x + forward.z * forward.z).toDouble())
        val pitch = Math.toDegrees(kotlin.math.atan2(forward.y.toDouble(), horizontal)).toFloat()
        return Vector3f(pitch, yaw, 0f)
    }

    private fun animatedPlaceholder(): String {
        return when ((System.currentTimeMillis() / 350L % 3L).toInt()) {
            0 -> "."
            1 -> ".."
            else -> "..."
        }
    }

    private fun texturesFor(style: SpeechBubbleStyle): BubbleTextures {
        return when (style.resolved()) {
            SpeechBubbleStyle.SQUARED -> BubbleTextures(SQUARED_BORDER, SQUARED_FILL)
            SpeechBubbleStyle.ROUNDED,
            SpeechBubbleStyle.CIRCULAR -> BubbleTextures(ROUNDED_BORDER, ROUNDED_FILL)
        }
    }

    private data class BubbleLayout(
        val lines: List<FormattedCharSequence>,
        val balloonWidth: Int,
        val baseX: Int,
        val baseY: Int,
        val padding: Int,
        val middleHeight: Int,
        val stackHeight: Float,
        val textBaseY: Int,
        val placeholder: Boolean
    ) {
        companion object {
            fun createStack(font: Font, text: String, language: Language, lineMode: SpeechBubbleLineMode, bubbleFont: SpeechBubbleFont, maxWidth: Int, extraPadding: Int): List<BubbleLayout> {
                val normalized = text.trim().ifBlank { "..." }
                val component = Component.literal(normalized).withStyle {
                    val selectedFont = fontForText(language, normalized, bubbleFont)
                    if (selectedFont == null) it else it.withFont(selectedFont)
                }
                val lines = font.split(component, maxWidth.coerceAtLeast(MIN_BALLOON_WIDTH))
                if (lineMode.resolved() == SpeechBubbleLineMode.SINGLE_LINE) {
                    return lines.ifEmpty { font.split(Component.literal("..."), maxWidth.coerceAtLeast(MIN_BALLOON_WIDTH)) }
                        .map { createFromLines(font, listOf(it), maxWidth, extraPadding, normalized) }
                }

                return listOf(createFromLines(font, lines, maxWidth, extraPadding, normalized))
            }

            private fun createFromLines(font: Font, lines: List<FormattedCharSequence>, maxWidth: Int, extraPadding: Int, fallbackText: String = "..."): BubbleLayout {
                val lineCount = lines.size.coerceAtLeast(1)
                val widest = lines.maxOfOrNull { font.width(it) } ?: font.width(fallbackText)

                var balloonWidth = widest.coerceIn(MIN_BALLOON_WIDTH, maxWidth.coerceAtLeast(MIN_BALLOON_WIDTH))
                if (balloonWidth % 2 == 0) {
                    balloonWidth--
                }

                val j = lineCount - 1
                val baseX = balloonWidth / 2
                val baseY = (-lineCount - (j * 7)) - j
                val padding = extraPadding
                val middleHeight = lineCount + (j * 8) + (padding * 2)
                val textBaseY = -(font.lineHeight * lineCount - (font.lineHeight + 1))
                val stackHeight = (font.lineHeight * lineCount + (padding * 2) + 2).toFloat()

                return BubbleLayout(
                    lines = lines,
                    balloonWidth = balloonWidth,
                    baseX = baseX,
                    baseY = baseY,
                    padding = padding,
                    middleHeight = middleHeight,
                    stackHeight = stackHeight,
                    textBaseY = textBaseY,
                    placeholder = fallbackText.trim() == "..."
                )
            }

            private fun fontForText(language: Language, text: String, bubbleFont: SpeechBubbleFont): ResourceLocation? {
                val resolvedFont = bubbleFont.resolved()
                if (resolvedFont == SpeechBubbleFont.MINECRAFT || language in DEFAULT_BUBBLE_FONT_LANGUAGES)
                    return null

                val needsDefaultFont = text.any { char ->
                    val block = Character.UnicodeBlock.of(char)
                    block in DEFAULT_BUBBLE_FONT_BLOCKS
                }

                if (needsDefaultFont)
                    return null

                return when (resolvedFont) {
                    SpeechBubbleFont.AUTO,
                    SpeechBubbleFont.NEXEL -> BUBBLE_FONT
                    SpeechBubbleFont.VANILLA_TWEAKS -> VANILLA_TWEAKS_FONT
                    SpeechBubbleFont.VANILLA_TWEAKS_ALT -> VANILLA_TWEAKS_ALT_FONT
                    SpeechBubbleFont.GALACTIC,
                    SpeechBubbleFont.TOONISH,
                    SpeechBubbleFont.ILLAGER,
                    SpeechBubbleFont.SUPREME -> BUBBLE_FONT
                    SpeechBubbleFont.MINECRAFT -> null
                }
            }
        }
    }

    private data class BubbleTextures(
        val border: ResourceLocation,
        val fill: ResourceLocation
    )

    private val DEFAULT_BUBBLE_FONT_LANGUAGES = setOf(
        Language.ARABIC,
        Language.BENGALI,
        Language.CHINESE,
        Language.CHINESE_TRADITIONAL,
        Language.GREEK,
        Language.HEBREW,
        Language.HINDI,
        Language.JAPANESE,
        Language.KOREAN,
        Language.PERSIAN,
        Language.RUSSIAN,
        Language.THAI,
        Language.UKRAINIAN,
        Language.URDU
    )

    private val DEFAULT_BUBBLE_FONT_BLOCKS = setOf(
        Character.UnicodeBlock.ARABIC,
        Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
        Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B,
        Character.UnicodeBlock.BENGALI,
        Character.UnicodeBlock.CJK_COMPATIBILITY,
        Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CYRILLIC,
        Character.UnicodeBlock.GREEK,
        Character.UnicodeBlock.GREEK_EXTENDED,
        Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
        Character.UnicodeBlock.HANGUL_JAMO,
        Character.UnicodeBlock.HANGUL_SYLLABLES,
        Character.UnicodeBlock.HEBREW,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.THAI
    )
}
