package com.arthou.ntranslator.client.gui

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
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
        private const val FRAME_RATE = 24
        private const val FRAME_COUNT = 270
        private const val FRAME_WIDTH = 960f
        private const val FRAME_HEIGHT = 540f
        private const val BLACK_HOLD_MS = 120L
        private const val DISPLAY_TIME_MS = 11250L
        private const val TITLE_SCREEN_DELAY_TICKS = 20
        private val STARTUP_SOUND = NTranslator.id("monlisc_startup")
        private val FRAMES = (1..FRAME_COUNT).map {
            NTranslator.id("textures/gui/monlisc_startup/frame_${it.toString().padStart(3, '0')}.png")
        }

        private var hasShownThisSession = false
        private var titleScreenTicks = 0

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
    private var playbackStarted = false
    private var blackStartedAt = 0L
    private var startupSound: SimpleSoundInstance? = null

    override fun init() {
        openedAt = 0L
        playbackStarted = false
        blackStartedAt = Util.getMillis()
        startupSound = null
    }

    override fun tick() {
        if (!playbackStarted)
            return

        val elapsed = Util.getMillis() - openedAt
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
            startPlayback()
        }

        val pose = guiGraphics.pose().last().pose()
        val elapsed = (Util.getMillis() - openedAt).coerceAtLeast(0L)
        val frameIndex = ((elapsed * FRAME_RATE) / 1000L).toInt().coerceIn(0, FRAME_COUNT - 1)
        val scale = minOf(width / FRAME_WIDTH, height / FRAME_HEIGHT)
        val drawWidth = FRAME_WIDTH * scale
        val drawHeight = FRAME_HEIGHT * scale
        val left = ((width - drawWidth) / 2.0f)
        val top = ((height - drawHeight) / 2.0f)
        val right = left + drawWidth
        val bottom = top + drawHeight

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionTexShader)
        RenderSystem.setShaderTexture(0, FRAMES[frameIndex])

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        builder.addVertex(pose, left, top, 0f).setUv(0f, 0f)
        builder.addVertex(pose, left, bottom, 0f).setUv(0f, 1f)
        builder.addVertex(pose, right, bottom, 0f).setUv(1f, 1f)
        builder.addVertex(pose, right, top, 0f).setUv(1f, 0f)
        BufferUploader.drawWithShader(builder.buildOrThrow())
        RenderSystem.disableBlend()
    }

    private fun startPlayback() {
        playbackStarted = true
        openedAt = Util.getMillis()
        val sound = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(STARTUP_SOUND), 1.0f, 1.0f)
        startupSound = sound
        minecraft?.soundManager?.play(sound)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        onClose()
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        onClose()
        return true
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        onClose()
        return true
    }

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun onClose() {
        startupSound?.let { minecraft?.soundManager?.stop(it) }
        startupSound = null
        minecraft?.setScreen(parent)
    }
}
