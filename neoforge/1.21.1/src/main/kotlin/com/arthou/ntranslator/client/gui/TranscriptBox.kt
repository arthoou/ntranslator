package com.arthou.ntranslator.client.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FastColor
import net.minecraft.world.entity.player.Player
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.events.TranscriptEvents
import com.arthou.ntranslator.transcript.Transcript
import com.arthou.ntranslator.translator.GoogleLineSegments
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import net.minecraft.util.FormattedCharSequence

data class TranscriptBox(
    var offsetX: Int,
    var offsetY: Int,
    var width: Int,
    var height: Int,
    var opacity: Int,

    var language: Language,

    var offsetXEdge: Boolean = false,
    var offsetYEdge: Boolean = false
) {
    var x: Int
        get() {
            return if (offsetXEdge)
                Minecraft.getInstance().window.guiScaledWidth - offsetX
            else
                offsetX
        }
        set(value) {
            if (value > Minecraft.getInstance().window.guiScaledWidth / 2 - (width / 2)) {
                offsetXEdge = true
                offsetX = Minecraft.getInstance().window.guiScaledWidth - value
            } else {
                offsetXEdge = false
                offsetX = value
            }
        }

    var y: Int
        get() {
            return if (offsetYEdge)
                Minecraft.getInstance().window.guiScaledHeight - offsetY
            else
                offsetY
        }
        set(value) {
            if (value > Minecraft.getInstance().window.guiScaledHeight / 2 - (height / 2)) {
                offsetYEdge = true
                offsetY = Minecraft.getInstance().window.guiScaledHeight - value
            } else {
                offsetYEdge = false
                offsetY = value
            }
        }

    @Transient
    private var cachedTranscripts: ConcurrentLinkedQueue<Transcript>? = ConcurrentLinkedQueue()

    @Transient
    private var cachedSegmentTexts: ConcurrentHashMap<TranscriptSegmentKey, MutableMap<Int, String>>? = ConcurrentHashMap()

    val transcripts: ConcurrentLinkedQueue<Transcript>
        get() {
            val existing = cachedTranscripts
            if (existing != null) {
                return existing
            }

            return ConcurrentLinkedQueue<Transcript>().also {
                cachedTranscripts = it
            }
        }

    fun render(guiGraphics: GuiGraphics, partialTick: Float) {
        val font = Minecraft.getInstance().font
        val panelOpacity = opacity.coerceIn(66, 170)
        val panelColor = FastColor.ARGB32.color(panelOpacity, 12, 16, 24)
        val headerText = Component.literal("NEXEL translations")
            .append(
                Component.literal("  ${language.displayCode}")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            )
        val headerHeight = 22
        val innerLeft = x + 2
        val innerTop = y + headerHeight
        val innerRight = x + width - 2
        val innerBottom = y + height - 2
        val innerWidth = (innerRight - innerLeft).coerceAtLeast(24)

        guiGraphics.pose().pushPose()

        guiGraphics.pose().translate(0.0, 0.0, -255.0)
        guiGraphics.enableScissor(x, y, x + width, y + height)

        guiGraphics.fill(x, y, x + width, y + height, panelColor)

        val headerScale = ((width - 10).toFloat() / font.width(headerText).coerceAtLeast(1).toFloat()).coerceAtMost(1f)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x + width / 2).toFloat(), (y + 6).toFloat(), 0f)
        guiGraphics.pose().scale(headerScale, headerScale, headerScale)
        guiGraphics.drawString(font, headerText, -font.width(headerText) / 2, 0, 0xF4F4F4, false)
        guiGraphics.pose().popPose()

        if (!NTranslatorClient.shouldTranscribe) {
            drawScaledIcon(guiGraphics, TRANSCRIPT_MUTED, x + width - 14, y + 6, 9)
        }

        guiGraphics.enableScissor(innerLeft, innerTop, innerRight, innerBottom)

        val scale = NTranslator.config.client.textScale / 100f
        val invScale = if (scale == 0f) 0f else 1f / scale
        val lineHeight = (font.lineHeight * scale).toInt().coerceAtLeast(1)
        val maxVisibleLines = ((innerBottom - innerTop - 2) / lineHeight).coerceAtLeast(1)
        val renderedLines = mutableListOf<TranscriptLine>()
        val textWidth = ((innerWidth - TRANSCRIPT_ICON_SPACE).coerceAtLeast(16) * invScale).toInt()

        for (transcript in transcripts.sortedBy { it.arrivalTime }) {
            val icon = if (System.currentTimeMillis() - transcript.arrivalTime <= SPEAKING_ICON_GRACE_MS) TRANSCRIPT_SPEAKING else TRANSCRIPT_IDLE
            val component = Component.empty()
                .append(
                    Component.literal(transcript.language.displayCode)
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                )
                .append(" ")
                .append(visiblePlayerName(transcript.player))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                .append(
                    Component.literal(transcript.text)
                        .withStyle(ChatFormatting.WHITE)
                        .apply {
                            if (transcript.incomplete) {
                                this.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                            }
                        }
                )

            val splitLines = font.split(component, textWidth)
            for ((lineIndex, line) in splitLines.withIndex()) {
                renderedLines += TranscriptLine(line, transcript, icon, lineIndex == 0)
            }
        }

        var currentY = innerTop + 2
        for ((line, _, icon, firstLine) in renderedLines.takeLast(maxVisibleLines)) {
            if (firstLine) {
                drawScaledIcon(guiGraphics, icon, innerLeft, currentY - 1, TRANSCRIPT_LINE_ICON_SIZE)
            }

            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate((innerLeft + TRANSCRIPT_ICON_SPACE).toFloat(), currentY.toFloat(), 0f)
            guiGraphics.pose().scale(scale, scale, scale)
            guiGraphics.drawString(font, line, 0, 0, 16777215)
            guiGraphics.pose().popPose()
            guiGraphics.setColor(1f, 1f, 1f, 1f)
            currentY += lineHeight
        }

        guiGraphics.disableScissor()
        guiGraphics.disableScissor()

        guiGraphics.pose().popPose()
    }

    fun updateTranscript(source: Player, text: String, language: Language, index: Int, updateTime: Long, incomplete: Boolean, ignoreRangeLimit: Boolean = false) {
        if (!UTVoiceChatCompat.isPlayerAudible(source))
            return

        val localPlayer = Minecraft.getInstance().player
        if (localPlayer != null && source.uuid != localPlayer.uuid && !ignoreRangeLimit && localPlayer.distanceToSqr(source) > TRANSCRIPT_CAPTURE_RANGE_SQR)
            return

        val displayIndex = GoogleLineSegments.baseIndex(index)
        val newestIndex = newestTranscriptIndex(source.uuid)
        if (newestIndex != null && displayIndex < newestIndex && transcripts.none { it.player.uuid == source.uuid && it.index == displayIndex }) {
            return
        }

        val displayText = mergedTranscriptText(source, text, language, index)

        if (this.transcripts.any { it.player.uuid == source.uuid && it.index == displayIndex }) {
            val transcript = this.transcripts.first { it.player.uuid == source.uuid && it.index == displayIndex }

            // it's possible for this to go out of order, let's avoid that
            if (transcript.lastUpdateTime > updateTime)
                return

            if (isPlaceholder(displayText) && !isPlaceholder(transcript.text))
                return

            transcript.lastUpdateTime = updateTime
            transcript.text = displayText
            transcript.incomplete = incomplete
            if (newestIndex == null || displayIndex >= newestIndex) {
                transcript.arrivalTime = System.currentTimeMillis()
            }

            TranscriptEvents.UPDATE.invoker().onTranscriptUpdate(transcript, this@TranscriptBox.language)

            return
        }

        this.transcripts.add(Transcript(displayIndex, source, displayText, language, updateTime, incomplete).apply {
            TranscriptEvents.UPDATE.invoker().onTranscriptUpdate(this, this@TranscriptBox.language)
        })
    }

    private fun mergedTranscriptText(source: Player, text: String, language: Language, index: Int): String {
        if (isPlaceholder(text)) {
            return text
        }

        val baseIndex = GoogleLineSegments.baseIndex(index)
        val segmentIndex = GoogleLineSegments.segmentIndex(index)
        val key = TranscriptSegmentKey(source.uuid, language, baseIndex)
        val segments = segmentTexts.computeIfAbsent(key) { mutableMapOf() }
        segments[segmentIndex] = text

        return segments
            .toSortedMap()
            .values
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { text }
    }

    private fun newestTranscriptIndex(playerId: UUID): Int? {
        return transcripts
            .filter { it.player.uuid == playerId }
            .maxOfOrNull { it.index }
    }

    private fun visiblePlayerName(player: Player): Component {
        val displayName = player.displayName
        if (displayName != null && displayName.string.isNotBlank()) {
            return displayName.copy()
        }

        return player.name.copy().withStyle(ChatFormatting.WHITE)
    }

    companion object {
        private const val TRANSCRIPT_CAPTURE_RANGE = 20.0
        private const val TRANSCRIPT_CAPTURE_RANGE_SQR = TRANSCRIPT_CAPTURE_RANGE * TRANSCRIPT_CAPTURE_RANGE
        private const val TRANSCRIPT_LINE_ICON_SIZE = 9
        private const val TRANSCRIPT_ICON_SPACE = TRANSCRIPT_LINE_ICON_SIZE + 4
        private const val SPEAKING_ICON_GRACE_MS = 1400L
        val TRANSCRIPT_MUTED = NTranslator.id("textures/gui/transcription_muted.png")
        val TRANSCRIPT_SPEAKING = NTranslator.id("textures/gui/transcript_speaking.png")
        val TRANSCRIPT_IDLE = NTranslator.id("textures/gui/transcript_idle.png")

        private fun isPlaceholder(text: String): Boolean {
            return text.trim() == "..."
        }
    }

    private fun drawScaledIcon(guiGraphics: GuiGraphics, icon: ResourceLocation, x: Int, y: Int, size: Int) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 1f)
        val scale = size / 16f
        guiGraphics.pose().scale(scale, scale, 1f)
        guiGraphics.blit(icon, 0, 0, 0f, 0f, 16, 16, 16, 16)
        guiGraphics.pose().popPose()
    }

    private data class TranscriptLine(
        val line: FormattedCharSequence,
        val transcript: Transcript,
        val icon: ResourceLocation,
        val firstLine: Boolean
    )

    private val segmentTexts: ConcurrentHashMap<TranscriptSegmentKey, MutableMap<Int, String>>
        get() {
            val existing = cachedSegmentTexts
            if (existing != null) {
                return existing
            }

            return ConcurrentHashMap<TranscriptSegmentKey, MutableMap<Int, String>>().also {
                cachedSegmentTexts = it
            }
        }

    private data class TranscriptSegmentKey(
        val playerId: UUID,
        val language: Language,
        val baseIndex: Int
    )
}
