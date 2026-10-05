package com.arthou.ntranslator.compat.voicechat

import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient

object UTVoiceChatCompat {
    val usesSimpleVoiceChat: Boolean
        get() {
            return NTranslator.instance.proxy.isModLoaded("voicechat")
        }

    val usesPlasmoVoice: Boolean
        get() {
            return NTranslator.instance.proxy.isModLoaded("plasmovoice")
        }

    val hasVoiceChat: Boolean
        get() {
            return usesSimpleVoiceChat || usesPlasmoVoice
        }

    fun getNearbyPlayers(source: ServerPlayer): List<ServerPlayer> {
        return if (usesSimpleVoiceChat)
            SimpleVoiceChatCompat.getNearbyPlayers(source)
        else if (usesPlasmoVoice)
            PlasmoVoiceChatCompat.getNearbyPlayers(source)
        else
            listOf(source)
    }

    fun isPlayerDeafened(player: ServerPlayer): Boolean {
        return if (usesSimpleVoiceChat)
            SimpleVoiceChatCompat.isPlayerDeafened(player)
        else if (usesPlasmoVoice)
            PlasmoVoiceChatCompat.isPlayerDeafened(player)
        else
            false
    }

    fun playerSharesGroup(player: ServerPlayer, other: ServerPlayer): Boolean {
        return if (usesSimpleVoiceChat)
            SimpleVoiceChatCompat.playerSharesGroup(player, other)
        else if (usesPlasmoVoice)
            PlasmoVoiceChatCompat.playerSharesGroup(player, other)
        else
            false
    }

    fun isPlayerAudible(player: Player): Boolean {
        if (player == Minecraft.getInstance().player)
            return true

        return if (usesSimpleVoiceChat)
            SimpleVoiceChatCompat.isPlayerAudible(player)
        else if (usesPlasmoVoice)
            PlasmoVoiceChatCompat.isPlayerAudible(player)
        else false
    }

    fun refreshLocalMuteState() {
        if (!NTranslator.config.client.muteTranscriptWhenVoiceChatMuted)
            return

        val muted = when {
            usesSimpleVoiceChat -> SimpleVoiceChatCompat.isLocalPlayerMuted()
            usesPlasmoVoice -> PlasmoVoiceChatCompat.isLocalPlayerMuted()
            else -> false
        }

        NTranslatorClient.shouldTranscribe = !muted
    }

    fun areBothSpectator(player: ServerPlayer, other: ServerPlayer): Boolean {
        if (player.gameMode.gameModeForPlayer == GameType.SPECTATOR && other.gameMode.gameModeForPlayer == GameType.SPECTATOR)
            return true
        else if (player.gameMode.gameModeForPlayer == GameType.SPECTATOR && other.gameMode.gameModeForPlayer != GameType.SPECTATOR)
            return false
        else if (player.gameMode.gameModeForPlayer != GameType.SPECTATOR && other.gameMode.gameModeForPlayer == GameType.SPECTATOR)
            return true

        return true
    }
}
