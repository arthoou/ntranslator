package com.arthou.ntranslator.client.bubbles

import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.config.SpeechBubbleAppearance
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.network.payloads.TypingBubblePayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import java.lang.reflect.Field

object ChatTypingBubbleWatcher {
    private const val HEARTBEAT_MS = 650L

    private var chatInputField: Field? = null
    private var chatInputLookupFailed = false
    private var wasTyping = false
    private var remoteWasTyping = false
    private var lastHeartbeatAt = 0L
    @Volatile private var bubbleOnlyChatActive = false

    fun setBubbleOnlyChatActive(enabled: Boolean) {
        bubbleOnlyChatActive = enabled
        if (!enabled) {
            clear()
        }
    }

    fun tick(mc: Minecraft) {
        val player = mc.player ?: return clear()

        if (!bubbleOnlyChatActive || mc.screen !is ChatScreen) {
            if (wasTyping) {
                SpeechBubbleManager.clearTyping(player.uuid)
                syncRemoteTyping(false)
            }
            wasTyping = false
            return
        }

        val input = getChatInput(mc.screen as ChatScreen)
        val currentValue = input?.value?.trim().orEmpty()
        val shouldShow = shouldShowTypingBubble(currentValue)

        if (shouldShow) {
            SpeechBubbleManager.showTyping(
                player,
                SpeechBubbleAppearance.fromConfig(NTranslator.config.client.speechBubbles)
            )
            syncRemoteTyping(true)
            wasTyping = true
        } else if (wasTyping) {
            SpeechBubbleManager.clearTyping(player.uuid)
            syncRemoteTyping(false)
            wasTyping = false
        }
    }

    fun clear() {
        val player = Minecraft.getInstance().player ?: return
        SpeechBubbleManager.clearTyping(player.uuid)
        syncRemoteTyping(false)
        wasTyping = false
    }

    private fun shouldShowTypingBubble(value: String): Boolean {
        return value.isNotBlank() && !value.startsWith("/")
    }

    private fun syncRemoteTyping(active: Boolean) {
        val player = Minecraft.getInstance().player ?: return
        if (!NTranslatorClient.connectedServerHasSupport) {
            remoteWasTyping = false
            return
        }

        val now = System.currentTimeMillis()
        if (active && remoteWasTyping && now - lastHeartbeatAt < HEARTBEAT_MS) {
            return
        }

        if (!active && !remoteWasTyping) {
            return
        }

        NTranslator.instance.proxy.sendPacketClient(
            TypingBubblePayload(
                player.uuid,
                active,
                SpeechBubbleAppearance.fromConfig(NTranslator.config.client.speechBubbles)
            )
        )
        remoteWasTyping = active
        lastHeartbeatAt = now
    }

    private fun getChatInput(chatScreen: ChatScreen): EditBox? {
        if (chatInputLookupFailed) {
            return null
        }

        return try {
            val field = chatInputField ?: ChatScreen::class.java.getDeclaredField("input").also {
                it.isAccessible = true
                chatInputField = it
            }

            field.get(chatScreen) as? EditBox
        } catch (_: ReflectiveOperationException) {
            chatInputLookupFailed = true
            null
        }
    }
}
