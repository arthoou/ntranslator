package com.arthou.ntranslator.client.bubbles

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.translator.GoogleLineSegments
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SpeechBubbleManager {
    private const val TYPING_BUBBLE_INDEX = Int.MAX_VALUE - 31
    private const val TYPING_BUBBLE_TIMEOUT_MS = 1_250L

    data class BubbleState(
        val playerId: UUID,
        val sourceLanguage: Language,
        var text: String,
        var index: Int,
        var lastUpdateTime: Long,
        var incomplete: Boolean,
        var appearance: SpeechBubbleAppearance,
        var arrivalTime: Long = System.currentTimeMillis(),
        var completedTime: Long? = if (incomplete) null else arrivalTime
    )

    private val activeBubbles = ConcurrentHashMap<UUID, MutableList<BubbleState>>()
    private val expiredBubbleKeys = ConcurrentHashMap<BubbleKey, Long>()
    private var currentLevelIdentity: Any? = null

    fun targetLanguage(): Language {
        return NTranslator.config.client.subtitleLanguage
    }

    fun update(source: Player, text: String, sourceLanguage: Language, index: Int, updateTime: Long, incomplete: Boolean, appearance: SpeechBubbleAppearance, respectAudibility: Boolean = true) {
        val localPlayer = Minecraft.getInstance().player
        val config = NTranslator.config.client.speechBubbles
        if (!appearance.visible || text.isBlank())
            return

        if (!config.enabled && source.uuid != localPlayer?.uuid)
            return

        if (respectAudibility && !UTVoiceChatCompat.isPlayerAudible(source)) {
            synchronized(activeBubbles) {
                activeBubbles.remove(source.uuid)
            }
            return
        }

        synchronized(activeBubbles) {
            pruneExpiredLocked()

            val list = activeBubbles.computeIfAbsent(source.uuid) { mutableListOf() }

            val newestBaseIndex = newestBaseIndex(list)
            val bubbleKey = BubbleKey(source.uuid, GoogleLineSegments.baseIndex(index))
            val existing = list.firstOrNull { it.index == index }
                ?: findRecentCorrectionTarget(list, text, index)

            // Translation futures can finish late. Never let an old phrase reappear after a newer one.
            if (GoogleLineSegments.baseIndex(index) < newestBaseIndex)
                return

            // If the placeholder/original bubble already aged out, a delayed translation must not recreate it.
            if (!incomplete && (expiredBubbleKeys.containsKey(bubbleKey) || isStaleFinalUpdate(updateTime))) {
                expiredBubbleKeys[bubbleKey] = System.currentTimeMillis()
                return
            }

            if (existing != null) {
                if (existing.lastUpdateTime > updateTime)
                    return

                if (isPlaceholder(text) && !isPlaceholder(existing.text))
                    return

                // If it already aged out, a delayed final translation must not resurrect it.
                if (isExpired(existing, System.currentTimeMillis())) {
                    list.remove(existing)
                    expiredBubbleKeys[BubbleKey(source.uuid, GoogleLineSegments.baseIndex(existing.index))] = System.currentTimeMillis()
                    return
                } else {
                    val previousText = existing.text
                    if (index > existing.index) {
                        existing.index = index
                    }
                    existing.text = text
                    existing.lastUpdateTime = updateTime
                    val wasIncomplete = existing.incomplete
                    existing.incomplete = incomplete
                    existing.appearance = appearance
                    if (incomplete) {
                        existing.completedTime = null
                    } else if (wasIncomplete || existing.completedTime == null || previousText != text) {
                        existing.completedTime = System.currentTimeMillis()
                        syncGoogleLineGroupCompletion(list, existing)
                    }
                    reorderNewestFirst(list)
                    return
                }
            }

            list.add(
                0,
                BubbleState(
                    playerId = source.uuid,
                    sourceLanguage = sourceLanguage,
                    text = text,
                    index = index,
                    lastUpdateTime = updateTime,
                    incomplete = incomplete,
                    appearance = appearance,
                    arrivalTime = System.currentTimeMillis()
                )
            )
            list.firstOrNull { it.index == index }?.let { syncGoogleLineGroupCompletion(list, it) }
            reorderNewestFirst(list)
        }
    }

    fun showTyping(source: Player, appearance: SpeechBubbleAppearance = SpeechBubbleAppearance.fromConfig(NTranslator.config.client.speechBubbles)) {
        val language = targetLanguage()
        val now = System.currentTimeMillis()

        synchronized(activeBubbles) {
            pruneExpiredLocked()

            val list = activeBubbles.computeIfAbsent(source.uuid) { mutableListOf() }
            val existing = list.firstOrNull { it.index == TYPING_BUBBLE_INDEX }

            if (existing != null) {
                existing.text = "..."
                existing.lastUpdateTime = now
                existing.incomplete = true
                existing.completedTime = null
                existing.appearance = appearance.copy(visible = true)
            } else {
                list.add(
                    0,
                    BubbleState(
                        playerId = source.uuid,
                        sourceLanguage = language,
                        text = "...",
                        index = TYPING_BUBBLE_INDEX,
                        lastUpdateTime = now,
                        incomplete = true,
                        appearance = appearance.copy(visible = true),
                        arrivalTime = now,
                        completedTime = null
                    )
                )
            }

            reorderNewestFirst(list)
        }
    }

    fun clearTyping(playerId: UUID) {
        synchronized(activeBubbles) {
            val list = activeBubbles[playerId] ?: return
            list.removeIf { it.index == TYPING_BUBBLE_INDEX }
            if (list.isEmpty()) {
                activeBubbles.remove(playerId)
            }
        }
    }

    fun markIncomplete(playerId: UUID, index: Int, incomplete: Boolean) {
        synchronized(activeBubbles) {
            pruneExpiredLocked()

            val list = activeBubbles[playerId] ?: return
            val newestBaseIndex = newestBaseIndex(list)
            if (GoogleLineSegments.baseIndex(index) < newestBaseIndex)
                return

            val current = list.firstOrNull { it.index == index } ?: return
            if (isExpired(current, System.currentTimeMillis()))
                return

            val wasIncomplete = current.incomplete
            current.incomplete = incomplete
            if (incomplete) {
                current.completedTime = null
            } else if (wasIncomplete || current.completedTime == null) {
                current.completedTime = System.currentTimeMillis()
                syncGoogleLineGroupCompletion(list, current)
            }
        }
    }

    fun remove(playerId: UUID, index: Int) {
        synchronized(activeBubbles) {
            val list = activeBubbles[playerId] ?: return
            list.removeIf { it.index == index }
            expiredBubbleKeys[BubbleKey(playerId, GoogleLineSegments.baseIndex(index))] = System.currentTimeMillis()
            if (list.isEmpty()) {
                activeBubbles.remove(playerId)
            }
        }
    }

    fun bubblesFor(playerId: UUID): List<BubbleState> {
        synchronized(activeBubbles) {
            pruneExpiredLocked()
            return activeBubbles[playerId]
                ?.sortedWith(
                    compareByDescending<BubbleState> { sortBaseIndex(it) }
                        .thenByDescending { sortSegmentIndex(it) }
                        .thenByDescending { it.lastUpdateTime }
                )
                ?.toList() ?: emptyList()
        }
    }

    fun alphaFor(state: BubbleState, currentTime: Long = System.currentTimeMillis()): Float {
        val config = NTranslator.config.client.speechBubbles
        val displayMs = (config.displaySeconds * 1000f).toLong()
        val fadeMs = (config.fadeSeconds * 1000f).toLong().coerceAtLeast(1L)
        val completedTime = state.completedTime ?: return 1.0f
        val fadeStart = completedTime + displayMs
        val fadeEnd = fadeStart + fadeMs

        if (currentTime <= fadeStart)
            return 1.0f

        return Mth.clamp((fadeEnd - currentTime).toFloat() / fadeMs.toFloat(), 0.0f, 1.0f)
    }

    fun tick() {
        synchronized(activeBubbles) {
            pruneExpiredLocked()

            val level = Minecraft.getInstance().level ?: return
            if (currentLevelIdentity !== level) {
                activeBubbles.clear()
                currentLevelIdentity = level
                return
            }

            activeBubbles.entries.removeIf { entry ->
                level.getPlayerByUUID(entry.key) == null || entry.value.isEmpty()
            }
        }
    }

    fun clear() {
        synchronized(activeBubbles) {
            activeBubbles.clear()
            expiredBubbleKeys.clear()
            currentLevelIdentity = Minecraft.getInstance().level
        }
    }

    private fun pruneExpiredLocked() {
        val config = NTranslator.config.client.speechBubbles
        val maxAge = ((config.displaySeconds + config.fadeSeconds) * 1000f).toLong()
        val incompleteMaxAge = (maxAge + 5000L).coerceAtLeast(12000L)
        val now = System.currentTimeMillis()

        activeBubbles.entries.removeIf { entry ->
            entry.value.removeIf { state ->
                val completedTime = state.completedTime
                val expired = if (completedTime != null) {
                    now - completedTime >= maxAge
                } else if (state.index == TYPING_BUBBLE_INDEX) {
                    now - state.lastUpdateTime >= TYPING_BUBBLE_TIMEOUT_MS
                } else {
                    now - state.lastUpdateTime >= incompleteMaxAge
                }
                if (expired && state.index != TYPING_BUBBLE_INDEX) {
                    expiredBubbleKeys[BubbleKey(entry.key, GoogleLineSegments.baseIndex(state.index))] = now
                }
                expired
            }
            entry.value.isEmpty()
        }

        expiredBubbleKeys.entries.removeIf { now - it.value >= EXPIRED_KEY_MEMORY_MS }
    }

    private fun isExpired(state: BubbleState, now: Long): Boolean {
        val config = NTranslator.config.client.speechBubbles
        val maxAge = ((config.displaySeconds + config.fadeSeconds) * 1000f).toLong()
        val completedTime = state.completedTime ?: return false
        return now - completedTime >= maxAge
    }

    private fun isStaleFinalUpdate(updateTime: Long): Boolean {
        val config = NTranslator.config.client.speechBubbles
        val maxAge = ((config.displaySeconds + config.fadeSeconds) * 1000f).toLong()
        return System.currentTimeMillis() - updateTime >= maxAge
    }

    private fun syncGoogleLineGroupCompletion(list: MutableList<BubbleState>, changed: BubbleState) {
        if (changed.incomplete || changed.completedTime == null)
            return

        val baseIndex = GoogleLineSegments.baseIndex(changed.index)
        val group = list.filter { GoogleLineSegments.baseIndex(it.index) == baseIndex && !it.incomplete }
        if (group.size <= 1)
            return

        val groupCompletedTime = group.mapNotNull { it.completedTime }.maxOrNull() ?: return
        group.forEach { it.completedTime = groupCompletedTime }
    }

    private fun reorderNewestFirst(list: MutableList<BubbleState>) {
        list.sortWith(
            compareByDescending<BubbleState> { sortBaseIndex(it) }
                .thenByDescending { sortSegmentIndex(it) }
                .thenByDescending { it.lastUpdateTime }
        )
    }

    private fun sortBaseIndex(state: BubbleState): Int {
        return if (state.index == TYPING_BUBBLE_INDEX) Int.MAX_VALUE else GoogleLineSegments.baseIndex(state.index)
    }

    private fun sortSegmentIndex(state: BubbleState): Int {
        return if (state.index == TYPING_BUBBLE_INDEX) Int.MAX_VALUE else GoogleLineSegments.segmentIndex(state.index)
    }

    private fun newestBaseIndex(list: List<BubbleState>): Int {
        return list
            .filterNot { it.index == TYPING_BUBBLE_INDEX }
            .maxOfOrNull { GoogleLineSegments.baseIndex(it.index) } ?: Int.MIN_VALUE
    }

    private fun isPlaceholder(text: String): Boolean {
        return text.trim() == "..."
    }

    private fun findRecentCorrectionTarget(list: MutableList<BubbleState>, text: String, index: Int): BubbleState? {
        val newest = list
            .filterNot { GoogleLineSegments.isDerivedIndex(it.index) }
            .maxByOrNull { it.index } ?: return null
        if (GoogleLineSegments.isDerivedIndex(index) || index <= newest.index || isPlaceholder(text) || isPlaceholder(newest.text))
            return null

        if (System.currentTimeMillis() - newest.lastUpdateTime > CORRECTION_WINDOW_MS)
            return null

        return if (looksLikeSamePhrase(newest.text, text)) newest else null
    }

    private fun looksLikeSamePhrase(previous: String, next: String): Boolean {
        val previousNormalized = normalizeForCorrection(previous)
        val nextNormalized = normalizeForCorrection(next)
        if (previousNormalized.isBlank() || nextNormalized.isBlank())
            return false

        return previousNormalized == nextNormalized ||
            previousNormalized.startsWith(nextNormalized) ||
            nextNormalized.startsWith(previousNormalized)
    }

    private fun normalizeForCorrection(text: String): String {
        return text
            .lowercase()
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private data class BubbleKey(val playerId: UUID, val baseIndex: Int)

    private const val EXPIRED_KEY_MEMORY_MS = 15 * 60 * 1000L
    private const val CORRECTION_WINDOW_MS = 30_000L
}
