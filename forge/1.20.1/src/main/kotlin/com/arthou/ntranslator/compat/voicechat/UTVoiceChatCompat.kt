package com.arthou.ntranslator.compat.voicechat

import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import com.arthou.ntranslator.NTranslator

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
        return runCatching {
            if (usesSimpleVoiceChat)
                SimpleVoiceChatCompat.getNearbyPlayers(source)
            else if (usesPlasmoVoice)
                PlasmoVoiceChatCompat.getNearbyPlayers(source)
            else
                listOf(source)
        }.getOrElse {
            listOf(source)
        }
    }

    fun isPlayerDeafened(player: ServerPlayer): Boolean {
        return runCatching {
            if (usesSimpleVoiceChat)
                SimpleVoiceChatCompat.isPlayerDeafened(player)
            else if (usesPlasmoVoice)
                PlasmoVoiceChatCompat.isPlayerDeafened(player)
            else
                false
        }.getOrDefault(false)
    }

    fun isPlayerAudible(player: Player): Boolean {
        if (player == Minecraft.getInstance().player)
            return true

        return runCatching {
            if (usesSimpleVoiceChat)
                SimpleVoiceChatCompat.isPlayerAudible(player)
            else if (usesPlasmoVoice)
                PlasmoVoiceChatCompat.isPlayerAudible(player)
            else false
        }.getOrDefault(true)
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
