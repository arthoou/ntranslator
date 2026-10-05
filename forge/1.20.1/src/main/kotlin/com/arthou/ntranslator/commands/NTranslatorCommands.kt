package com.arthou.ntranslator.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.server.level.ServerPlayer
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.network.UTServerNetworking
import com.arthou.ntranslator.translator.LocalLibreTranslateInstance
import com.arthou.ntranslator.translator.TranslatorManager

object NTranslatorCommands {
    val INFO = Commands.literal("info")
        .executes { ctx ->
            ctx.source.sendSystemMessage(ComponentUtils.formatList(listOf(
                Component.literal("NEXEL ${NTranslator.instance.proxy.modVersion}"),
                Component.literal("- Total instances loaded: ${TranslatorManager.instances.size}"),
                Component.literal("- Queued translations: ${TranslatorManager.queuedTranslations.size}"),
                Component.empty(),
                Component.literal("- Supports local translation server: ${LocalLibreTranslateInstance.canRunLibreTranslate()}"),
                Component.literal("- Is local translation server running: ${LocalLibreTranslateInstance.hasStarted}"),
                Component.literal("- Supports CUDA: ${TranslatorManager.supportsCuda}"),
            ), Component.literal("\n")))

            1
        }

    val CLEAR_QUEUE = Commands.literal("clearqueue")
        .executes { ctx ->
            TranslatorManager.queuedTranslations.clear()
            ctx.source.sendSystemMessage(Component.literal("Forcefully cleared translation queue."))

            1
        }

    val DEBUG_RESTART = Commands.literal("debugreload")
        .executes { ctx ->
            TranslatorManager.installLibreTranslate(true)
            ctx.source.sendSystemMessage(Component.literal("Forced local translation server startup. Check /nexel info again in a few seconds."))

            1
        }

    val ROOT = Commands.literal("nexel")
        .requires { it.hasPermission(3) }
        .then(INFO)
        .then(CLEAR_QUEUE)
        .then(DEBUG_RESTART)

    val NCHAT = Commands.literal("nchat")
        .requires { it.hasPermission(4) }
        .then(Commands.argument("nick", StringArgumentType.string())
            .suggests { ctx, builder ->
                SharedSuggestionProvider.suggest(ctx.source.server.playerList.players.map { it.gameProfile.name }, builder)
            }
            .executes { ctx ->
                val nick = StringArgumentType.getString(ctx, "nick")
                val target = findPlayer(ctx.source.server.playerList.players, nick)

                if (target == null) {
                    ctx.source.sendFailure(Component.literal("Player \"$nick\" is not online."))
                    return@executes 0
                }

                val enabled = UTServerNetworking.toggleBubbleChat(target)
                val message = if (enabled) {
                    "Bubble-only chat enabled for ${target.scoreboardName}."
                } else {
                    "Bubble-only chat disabled for ${target.scoreboardName}."
                }

                ctx.source.sendSuccess({ Component.literal(message) }, true)

                target.sendSystemMessage(
                    Component.literal(
                        if (enabled)
                            "Your messages will now appear only as translated speech bubbles."
                        else
                            "Your messages will appear in normal chat again."
                    )
                )

                1
            })

    val NTTS = Commands.literal("ntts")
        .requires { it.entity is ServerPlayer }
        .executes { ctx ->
            val player = ctx.source.entity as? ServerPlayer ?: run {
                ctx.source.sendFailure(Component.literal("Only players can use /ntts."))
                return@executes 0
            }

            val enabled = UTServerNetworking.toggleBubbleTts(player)
            ctx.source.sendSuccess({
                Component.literal(
                    if (enabled) {
                        "NEXEL bubble TTS enabled. You will hear nearby translated bubbles."
                    } else {
                        "NEXEL bubble TTS disabled."
                    }
                )
            }, false)
            1
        }

    private fun findPlayer(players: List<ServerPlayer>, nick: String): ServerPlayer? {
        return players.firstOrNull {
            it.gameProfile.name.equals(nick, true) ||
                it.scoreboardName.equals(nick, true) ||
                it.name.string.equals(nick, true)
        }
    }
}
