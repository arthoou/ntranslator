package com.arthou.ntranslator.client.gui

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import com.arthou.ntranslator.NTranslator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat

class MonliscStartupScreen(private val parent: Screen) : Screen(Component.empty()) {
    companion object {
        private const val SESSION_OWNER_PROPERTY = "monlisc.technologies.startup.owner"
        private val IMAGE = NTranslator.id("textures/gui/monlisc_technologies.png")
        private const val IMAGE_WIDTH = 1600
        private const val IMAGE_HEIGHT = 900
        private const val CROPPED_U = 180f
        private const val CROPPED_V = 210f
        private const val CROPPED_WIDTH = 1240f
        private const val CROPPED_HEIGHT = 500f
        private const val BLACK_HOLD_MS = 180L
        private const val DISPLAY_TIME_MS = 2400L
        private const val TITLE_SCREEN_DELAY_TICKS = 20

        private var hasShownThisSession = false
        private var titleScreenTicks = 0

        private data class StartupNote(
            val eventId: ResourceLocation,
            val delayMs: Long,
            val volume: Float,
            val pitch: Float
        )

        private val notes = listOf(
            StartupNote(ResourceLocation.withDefaultNamespace("block.note_block.bell"), 80L, 0.90f, 0.88f),
            StartupNote(ResourceLocation.withDefaultNamespace("block.note_block.bell"), 220L, 0.95f, 1.00f),
            StartupNote(ResourceLocation.withDefaultNamespace("block.note_block.chime"), 390L, 1.00f, 1.14f),
            StartupNote(ResourceLocation.withDefaultNamespace("block.note_block.chime"), 620L, 1.05f, 0.76f)
        )

        fun maybeOpen(mc: Minecraft) {
            if (hasShownThisSession || isStartupSessionHandled()) {
                hasShownThisSession = true
                return
            }

            val current = mc.screen
            if (isAnyMonliscStartupScreen(current)) {
                rememberExternalStartup()
                hasShownThisSession = true
                titleScreenTicks = 0
                return
            }

            if (current !is TitleScreen) {
                titleScreenTicks = 0
                return
            }

            titleScreenTicks++
            if (titleScreenTicks < TITLE_SCREEN_DELAY_TICKS)
                return

            if (!claimStartupSession(NTranslator.MOD_ID)) {
                hasShownThisSession = true
                titleScreenTicks = 0
                return
            }

            hasShownThisSession = true
            titleScreenTicks = 0
            mc.setScreen(MonliscStartupScreen(current))
        }

        private fun isAnyMonliscStartupScreen(screen: Screen?): Boolean {
            return screen != null && screen.javaClass.simpleName == MonliscStartupScreen::class.java.simpleName
        }

        private fun isStartupSessionHandled(): Boolean {
            return synchronized(System.getProperties()) {
                !System.getProperty(SESSION_OWNER_PROPERTY).isNullOrBlank()
            }
        }

        private fun claimStartupSession(owner: String): Boolean {
            return synchronized(System.getProperties()) {
                if (!System.getProperty(SESSION_OWNER_PROPERTY).isNullOrBlank()) {
                    false
                } else {
                    System.setProperty(SESSION_OWNER_PROPERTY, owner)
                    true
                }
            }
        }

        private fun rememberExternalStartup() {
            synchronized(System.getProperties()) {
                if (System.getProperty(SESSION_OWNER_PROPERTY).isNullOrBlank()) {
                    System.setProperty(SESSION_OWNER_PROPERTY, "external")
                }
            }
        }
    }

    private var openedAt = 0L
    private var nextNoteIndex = 0
    private var playbackStarted = false
    private var blackStartedAt = 0L

    override fun init() {
        openedAt = 0L
        nextNoteIndex = 0
        playbackStarted = false
        blackStartedAt = Util.getMillis()
    }

    override fun tick() {
        if (!playbackStarted)
            return

        val elapsed = Util.getMillis() - openedAt
        while (nextNoteIndex < notes.size && elapsed >= notes[nextNoteIndex].delayMs) {
            val note = notes[nextNoteIndex]
            minecraft?.soundManager?.play(
                SimpleSoundInstance.forUI(
                    SoundEvent.createVariableRangeEvent(note.eventId),
                    note.volume,
                    note.pitch
                )
            )
            nextNoteIndex++
        }

        if (elapsed >= DISPLAY_TIME_MS) {
            onClose()
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xFF000000.toInt())

        if (Util.getMillis() - blackStartedAt < BLACK_HOLD_MS) {
            return
        }

        if (!playbackStarted) {
            playbackStarted = true
            openedAt = Util.getMillis()
        }

        val pose = guiGraphics.pose().last().pose()
        val minU = CROPPED_U / IMAGE_WIDTH
        val maxU = (CROPPED_U + CROPPED_WIDTH) / IMAGE_WIDTH
        val minV = CROPPED_V / IMAGE_HEIGHT
        val maxV = (CROPPED_V + CROPPED_HEIGHT) / IMAGE_HEIGHT
        val scale = minOf(width / CROPPED_WIDTH, height / CROPPED_HEIGHT)
        val drawWidth = CROPPED_WIDTH * scale
        val drawHeight = CROPPED_HEIGHT * scale
        val left = ((width - drawWidth) / 2.0f)
        val top = ((height - drawHeight) / 2.0f)
        val right = left + drawWidth
        val bottom = top + drawHeight

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionTexShader)
        RenderSystem.setShaderTexture(0, IMAGE)

        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        builder.vertex(pose, left, top, 0f).uv(minU, minV).endVertex()
        builder.vertex(pose, left, bottom, 0f).uv(minU, maxV).endVertex()
        builder.vertex(pose, right, bottom, 0f).uv(maxU, maxV).endVertex()
        builder.vertex(pose, right, top, 0f).uv(maxU, minV).endVertex()
        BufferUploader.drawWithShader(builder.end())
        RenderSystem.disableBlend()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (playbackStarted && Util.getMillis() - openedAt > 250L) {
            onClose()
        }

        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (playbackStarted && Util.getMillis() - openedAt > 250L) {
            onClose()
        }

        return true
    }

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }
}
