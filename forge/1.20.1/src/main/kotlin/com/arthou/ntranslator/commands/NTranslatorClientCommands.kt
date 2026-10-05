package com.arthou.ntranslator.commands

import dev.architectury.event.events.client.ClientCommandRegistrationEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.client.transcribers.browser.BrowserSpeechTranscriber
import com.arthou.ntranslator.translator.LocalLibreTranslateInstance
import com.arthou.ntranslator.translator.TranslatorManager

object NTranslatorClientCommands {
    val TRANSCRIBER = ClientCommandRegistrationEvent.literal("checktranscriber")
        .executes {
            if (NTranslatorClient.transcriber is BrowserSpeechTranscriber) {
                (NTranslatorClient.transcriber as BrowserSpeechTranscriber).openWebsite()
            }

            it.source.`arch$sendSuccess`({ Component.literal("Reopening browser transcriber if not opened") }, false)

            1
        }

    val INFO = ClientCommandRegistrationEvent.literal("info")
        .executes {
            it.source.`arch$sendSuccess`({
                ComponentUtils.formatList(
                    listOf(
                        Component.literal("NEXEL ${NTranslator.instance.proxy.modVersion}"),
                        Component.literal("- Enabled: ${NTranslator.config.client.enabled}"),
                        Component.literal("- Current transcriber: ${NTranslator.config.client.transcriber}"),
                        Component.literal("- Spoken language: ${NTranslator.config.client.spokenLanguage}"),
                        Component.literal("- Subtitles language: ${NTranslator.config.client.subtitleLanguage}"),
                        Component.empty(),
                        Component.literal("- Server supports NEXEL: ${NTranslatorClient.connectedServerHasSupport}"),
                        Component.literal("- Supports local translation server: ${LocalLibreTranslateInstance.canRunLibreTranslate()}"),
                        Component.literal("- Is local translation server running: ${LocalLibreTranslateInstance.hasStarted}"),
                        Component.literal("- Supports CUDA: ${TranslatorManager.supportsCuda}"),
                    ), Component.literal("\n")
                )
            }, false)

            1
        }

    val OPEN_BROWSER = ClientCommandRegistrationEvent.literal("openbrowser")
        .executes {
            if (NTranslatorClient.transcriber is BrowserSpeechTranscriber) {
                (NTranslatorClient.transcriber as BrowserSpeechTranscriber).openWebsite()
            }

            1
        }

    val ROOT = ClientCommandRegistrationEvent.literal("nexelclient")
        .then(TRANSCRIBER)
        .then(INFO)
        .then(OPEN_BROWSER)

    fun init() {
        ClientCommandRegistrationEvent.EVENT.register { dispatcher, ctx ->
            dispatcher.register(ROOT)
        }
    }
}
