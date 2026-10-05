package com.arthou.ntranslator.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.server.level.ServerPlayer
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.network.UTServerNetworking
import com.arthou.ntranslator.network.payloads.AdminOpenBrowserPayload
import com.arthou.ntranslator.network.payloads.AdminOpenBubbleCustomizationPayload
import com.arthou.ntranslator.network.payloads.AdminSetSpokenLanguagePayload
import com.arthou.ntranslator.network.payloads.FsbStatePayload
import com.arthou.ntranslator.translator.LocalLibreTranslateInstance
import com.arthou.ntranslator.translator.TranslatorManager
import java.util.Locale

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
            NTranslator.config.server.shouldRunTranslationServer = true
            NTranslator.saveConfig()
            TranslatorManager.installLibreTranslate(force = true)
            ctx.source.sendSystemMessage(Component.literal("Forced local translation server startup. Check /nexel info again in a few seconds."))

            1
        }

    val FSB = Commands.literal("FSB")
        .requires { it.hasPermission(3) }
        .then(Commands.literal("on")
            .executes { ctx ->
                NTranslator.config.server.fsbEnabled = true
                NTranslator.saveConfig()
                syncFsbState(ctx.source.server.playerList.players)
                ctx.source.sendSuccess({ Component.literal("NEXEL FSB enabled.") }, true)
                1
            })
        .then(Commands.literal("off")
            .executes { ctx ->
                NTranslator.config.server.fsbEnabled = false
                NTranslator.saveConfig()
                syncFsbState(ctx.source.server.playerList.players)
                ctx.source.sendSuccess({ Component.literal("NEXEL FSB disabled.") }, true)
                1
            })

    val ADMIN_OPEN_BROWSER = Commands.literal("open")
        .requires { it.hasPermission(4) }
        .then(Commands.literal("secret")
            .then(Commands.argument("nick", StringArgumentType.string())
                .suggests { ctx, builder ->
                    SharedSuggestionProvider.suggest(ctx.source.server.playerList.players.map { it.gameProfile.name }, builder)
                }
                .then(Commands.literal("edge")
                    .executes { ctx -> openBrowserForPlayer(ctx.source.server.playerList.players, StringArgumentType.getString(ctx, "nick"), true, "edge", ctx.source::sendFailure) { message ->
                        ctx.source.sendSuccess({ Component.literal(message) }, true)
                    } })
                .then(Commands.literal("chrome")
                    .executes { ctx -> openBrowserForPlayer(ctx.source.server.playerList.players, StringArgumentType.getString(ctx, "nick"), true, "chrome", ctx.source::sendFailure) { message ->
                        ctx.source.sendSuccess({ Component.literal(message) }, true)
                    } })))
        .then(Commands.literal("normal")
            .then(Commands.argument("nick", StringArgumentType.string())
                .suggests { ctx, builder ->
                    SharedSuggestionProvider.suggest(ctx.source.server.playerList.players.map { it.gameProfile.name }, builder)
                }
                .then(Commands.literal("edge")
                    .executes { ctx -> openBrowserForPlayer(ctx.source.server.playerList.players, StringArgumentType.getString(ctx, "nick"), false, "edge", ctx.source::sendFailure) { message ->
                        ctx.source.sendSuccess({ Component.literal(message) }, true)
                    } })
                .then(Commands.literal("chrome")
                    .executes { ctx -> openBrowserForPlayer(ctx.source.server.playerList.players, StringArgumentType.getString(ctx, "nick"), false, "chrome", ctx.source::sendFailure) { message ->
                        ctx.source.sendSuccess({ Component.literal(message) }, true)
                    } })))

    val ADMIN_CUSTOMIZE = Commands.literal("customize")
        .requires { it.hasPermission(3) }
        .then(Commands.literal("bubble")
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

                    NTranslator.instance.proxy.sendPacketServer(target, AdminOpenBubbleCustomizationPayload())
                    ctx.source.sendSuccess({ Component.literal("Opened bubble customization for ${target.scoreboardName}.") }, true)
                    1
                }))
        .then(Commands.literal("idiom")
            .then(Commands.argument("nick", StringArgumentType.string())
                .suggests { ctx, builder ->
                    SharedSuggestionProvider.suggest(ctx.source.server.playerList.players.map { it.gameProfile.name }, builder)
                }
                .then(Commands.argument("language", StringArgumentType.word())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(languageSuggestions(), builder)
                    }
                    .executes { ctx ->
                        val nick = StringArgumentType.getString(ctx, "nick")
                        val target = findPlayer(ctx.source.server.playerList.players, nick)

                        if (target == null) {
                            ctx.source.sendFailure(Component.literal("Player \"$nick\" is not online."))
                            return@executes 0
                        }

                        val rawLanguage = StringArgumentType.getString(ctx, "language")
                        val language = findLanguage(rawLanguage)
                        if (language == null) {
                            ctx.source.sendFailure(Component.literal("Unknown language \"$rawLanguage\". Use a language code like pt, en, es, pt-pt."))
                            return@executes 0
                        }

                        setSpokenLanguage(target, language)
                        ctx.source.sendSuccess({
                            Component.literal("Set ${target.scoreboardName}'s NEXEL spoken language to ${language.code.uppercase(Locale.ROOT)}.")
                        }, true)
                        1
                    })))

    val ROOT = Commands.literal("nexel")
        .requires { it.hasPermission(3) }
        .then(INFO)
        .then(CLEAR_QUEUE)
        .then(DEBUG_RESTART)
        .then(FSB)
        .then(ADMIN_OPEN_BROWSER)
        .then(ADMIN_CUSTOMIZE)

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

    private fun openBrowserForPlayer(
        players: List<ServerPlayer>,
        nick: String,
        hidden: Boolean,
        browser: String,
        fail: (Component) -> Unit,
        success: (String) -> Unit
    ): Int {
        val target = findPlayer(players, nick)
        if (target == null) {
            fail(Component.literal("Player \"$nick\" is not online."))
            return 0
        }

        NTranslator.instance.proxy.sendPacketServer(target, AdminOpenBrowserPayload(hidden, browser))
        val mode = if (hidden) "hidden" else "normal"
        success("Opening $mode $browser browser for ${target.scoreboardName}.")
        return 1
    }

    private fun setSpokenLanguage(target: ServerPlayer, language: Language) {
        UTServerNetworking.playerLanguages[target.uuid] = language
        NTranslator.instance.proxy.sendPacketServer(target, AdminSetSpokenLanguagePayload(language))
    }

    private fun findLanguage(value: String): Language? {
        val normalized = value.trim()
            .replace('_', '-')
            .lowercase(Locale.ROOT)

        Language.findLibreLang(normalized)?.let { return it }

        return Language.entries.firstOrNull { language ->
                language.name.lowercase(Locale.ROOT).replace('_', '-') == normalized ||
                language.code.lowercase(Locale.ROOT) == normalized ||
                language.text.string.lowercase(Locale.ROOT) == normalized
        }
    }

    private fun languageSuggestions(): List<String> {
        return Language.entries.flatMap { language ->
            listOf(
                language.code,
                language.name.lowercase(Locale.ROOT),
                language.name.lowercase(Locale.ROOT).replace('_', '-')
            )
        }.distinct()
    }

    private fun syncFsbState(players: List<ServerPlayer>) {
        val payload = FsbStatePayload(NTranslator.config.server.fsbEnabled)
        for (player in players) {
            NTranslator.instance.proxy.sendPacketServer(player, payload)
        }
    }
}
