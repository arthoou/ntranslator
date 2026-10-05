package com.arthou.ntranslator.client.transcribers.browser

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import dev.architectury.event.events.client.ClientPlayerEvent
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.HttpUtil
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.client.gui.OpenBrowserScreen
import com.arthou.ntranslator.client.gui.RequestDownloadScreen
import com.arthou.ntranslator.client.transcribers.SpeechTranscriber
import com.arthou.ntranslator.client.transcribers.TranscriberType
import com.arthou.ntranslator.client.transcribers.browser.bridge.BrowserBridgeLauncher
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.config.BubbleVoice
import com.arthou.ntranslator.config.SpeechBubbleModeRules
import com.arthou.ntranslator.events.BrowserSpeechEvents
import java.net.InetSocketAddress

class BrowserSpeechTranscriber(language: Language) : SpeechTranscriber(language) {
    val socketPort = HttpUtil.getAvailablePort()
    val server: HttpServer
    val socket = BrowserSocket()
    val serverPort = if (!HttpUtil.isPortAvailable(25117))
        HttpUtil.getAvailablePort()
    else
        25117

    init {
        BrowserApplication.socketPort = socketPort
        server = HttpServer.create(InetSocketAddress("0.0.0.0", serverPort), 0)
        BrowserApplication.addHandler(server)

        server.start()

        socket.isDaemon = true
        socket.start()

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { _ ->
            openWebsite()
        }
    }

    fun openWebsite() {
        val mc = Minecraft.getInstance()
        val url = "http://127.0.0.1:$serverPort"

        if (socket.totalConnections <= 0 && NTranslator.config.client.enabled) {
            if (NTranslator.config.client.openBrowserWithoutPrompt) {
                openTranscriberPage(url)
            } else {
                Minecraft.getInstance().execute {
                    if (mc.screen is RequestDownloadScreen) {
                        (mc.screen as RequestDownloadScreen).parent = OpenBrowserScreen(url)
                    } else {
                        mc.setScreen(OpenBrowserScreen(url))
                    }
                }
            }
        }
    }

    fun reopenWebsiteForBrowserChange() {
        val url = "http://127.0.0.1:$serverPort"
        BrowserBridgeLauncher.stop()
        openTranscriberPage(url)
    }

    fun openFromAdmin(hidden: Boolean, browser: String) {
        val url = "http://127.0.0.1:$serverPort"
        val normalizedBrowser = if (browser.equals("chrome", true)) "chrome" else "edge"

        if (!hidden) {
            if (!openVisibleBrowser(url, normalizedBrowser)) {
                Util.getPlatform().openUri(url)
            }
            return
        }

        NTranslator.config.server.preferChromeBrowser = normalizedBrowser == "chrome"
        NTranslator.config.server.preferEdgeBrowser = normalizedBrowser == "edge"
        NTranslator.config.server.lastHiddenBrowser = normalizedBrowser
        NTranslator.saveConfig()

        BrowserBridgeLauncher.stop()
        val launched = BrowserBridgeLauncher.open(url, normalizedBrowser)
        if (!launched) {
            NTranslatorClient.displayMessage(
                Component.literal("Could not start hidden $normalizedBrowser browser bridge."),
                true
            )
        }
    }

    private fun openVisibleBrowser(url: String, browser: String): Boolean {
        if (Util.getPlatform() != Util.OS.WINDOWS) {
            return false
        }

        return runCatching {
            val command = if (browser == "chrome") {
                listOf("cmd.exe", "/c", "start", "", "chrome", url)
            } else {
                listOf("cmd.exe", "/c", "start", "", "microsoft-edge:$url")
            }

            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        }.getOrDefault(false)
    }

    private fun openTranscriberPage(url: String) {
        val hiddenBrowser = selectedHiddenBrowser()
        if (hiddenBrowser == null) {
            BrowserBridgeLauncher.stop()
            Util.getPlatform().openUri(url)
            return
        }

        val launched = BrowserBridgeLauncher.open(url, hiddenBrowser)
        if (!launched) {
            NTranslatorClient.displayMessage(
                Component.literal("Could not start hidden $hiddenBrowser browser bridge."),
                true
            )
        }
    }

    private fun restartTranscriberPage() {
        val url = "http://127.0.0.1:$serverPort"
        val hiddenBrowser = selectedHiddenBrowser()
        if (hiddenBrowser == null) {
            BrowserBridgeLauncher.stop()
            Util.getPlatform().openUri(url)
            return
        }

        val restarted = BrowserBridgeLauncher.open(url, hiddenBrowser)
        if (!restarted) {
            NTranslatorClient.displayMessage(
                Component.literal("Could not restart hidden $hiddenBrowser browser bridge."),
                true
            )
        }
    }

    private fun selectedHiddenBrowser(): String? {
        val selected = when {
            NTranslator.config.server.preferEdgeBrowser -> "edge"
            NTranslator.config.server.preferChromeBrowser -> "chrome"
            else -> null
        }
        if (selected != null) {
            NTranslator.config.server.lastHiddenBrowser = selected
        }
        return selected
    }

    override fun stop() {
        BrowserBridgeLauncher.stop()
        server.stop(0)
        socket.stop(1000)
    }

    override fun changeLanguage(language: Language) {
        super.changeLanguage(language)
        this.socket.broadcast("set_language", JsonObject().apply {
            addProperty("language", language.supportedTranscribers[TranscriberType.BROWSER])
        })
    }

    override fun setMuted(muted: Boolean) {
        this.socket.broadcast("set_muted", JsonObject().apply {
            this.addProperty("muted", muted)
        })
    }

    fun syncBubbleAppearance() {
        val config = NTranslator.config.client.speechBubbles

        this.socket.broadcast("set_bubble_appearance", JsonObject().apply {
            addProperty("style", config.style.resolved().name)
            addProperty("lineMode", SpeechBubbleModeRules.effectiveLineMode(config).name)
            addProperty("font", config.font.name)
            addProperty("borderColor", config.borderColor)
            addProperty("fillColor", config.fillColor)
            addProperty("textColor", config.textColor)
            addProperty("maxWidth", config.maxWidth)
            addProperty("padding", config.padding)
            addProperty("displaySeconds", config.displaySeconds)
            addProperty("fadeSeconds", config.fadeSeconds)
        })
    }

    fun speak(text: String, language: Language, voice: BubbleVoice) {
        speakInternal(text, language, voice, false)
    }

    fun speakMuffled(text: String, language: Language, voice: BubbleVoice) {
        speakInternal(text, language, voice, true)
    }

    private fun speakInternal(text: String, language: Language, voice: BubbleVoice, muffled: Boolean) {
        if (voice == BubbleVoice.OFF || text.isBlank())
            return

        this.socket.broadcast("speak", JsonObject().apply {
            addProperty("text", text)
            addProperty("language", language.supportedTranscribers[TranscriberType.BROWSER] ?: language.code)
            addProperty("voice", voice.browserId)
            addProperty("muffled", muffled)
        })
    }

    fun stopSpeech() {
        this.socket.broadcast("stop_speech")
    }

    inner class BrowserSocket : WebSocketServer(InetSocketAddress("0.0.0.0", socketPort)) {
        var totalConnections = 0

        override fun onOpen(ws: WebSocket, handshake: ClientHandshake) {
            ws.sendData("set_language", JsonObject().apply {
                addProperty("language", language.supportedTranscribers[TranscriberType.BROWSER])
            })
            totalConnections++

            NTranslatorClient.displayMessage(Component.translatable("ntranslator.transcriber.connected"))
            UTVoiceChatCompat.refreshLocalMuteState()
            setMuted(!NTranslatorClient.shouldTranscribe)
            syncBubbleAppearance()
        }

        override fun onClose(ws: WebSocket, code: Int, reason: String, remote: Boolean) {
            totalConnections--

            NTranslatorClient.displayMessage(Component.translatable("ntranslator.transcriber.disconnected")
                .withStyle {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, "http://127.0.0.1:${serverPort}"))
                })
        }

        override fun onMessage(ws: WebSocket, message: String) {
            val msg = JsonParser.parseString(message).asJsonObject
            val data = if (msg.has("d")) msg.getAsJsonObject("d") else JsonObject()

            when (msg.get("op").asString) {
                "transcript" -> {
                    val index = if (data.has("index")) data.get("index").asInt else lastIndex + 1
                    val isFinal = data.get("final")?.asBoolean ?: true

                    if (data.has("text")) {
                        val text = data.get("text").asString.trim()
                        val translatedIndex = currentOffset + index
                        if (text.isNotBlank()) {
                            if (isFinal) {
                                BrowserSpeechEvents.FINAL_TRANSCRIPT.invoker().onFinalTranscript(translatedIndex, text, language)
                            }
                            updater.accept(translatedIndex, text, isFinal)
                        }

                        lastIndex = translatedIndex
                        return
                    }

                    val results = data.getAsJsonArray("results")

                    val deserialized = mutableListOf<Pair<String, Double>>()
                    for (result in results) {
                        val d = result.asJsonObject
                        deserialized.add(d.get("text").asString to d.get("confidence").asDouble)
                    }

                    if (deserialized.isEmpty()) {
                        lastIndex = currentOffset + index
                        return
                    }

                    val selected = deserialized.sortedByDescending { it.second }[0].first

                    if (selected.isNotBlank()) {
                        val selectedText = selected.trim()
                        BrowserSpeechEvents.FINAL_TRANSCRIPT.invoker().onFinalTranscript(currentOffset + index, selectedText, language)
                        updater.accept(currentOffset + index, selectedText, true)
                    }

                    lastIndex = currentOffset + index
                }

                "reset" -> {
                    currentOffset = lastIndex + 1
                }

                "speech_state" -> {
                    BrowserSpeechEvents.SPEECH_STATE.invoker().onSpeechState(data.get("speaking")?.asBoolean ?: false)
                }

                "error" -> {
                    val type = data.get("type").asString

                    NTranslatorClient.displayMessage(Component.translatable("ntranslator.transcriber.error")
                        .append(Component.translatable("ntranslator.transcriber.error.$type")), true)

                    if (type !in setOf("no_support", "not-allowed", "audio-capture")) {
                        restartTranscriberPage()
                    }
                }
            }
        }

        override fun onError(ws: WebSocket, ex: Exception) {
            ex.printStackTrace()
        }

        override fun onStart() {
            NTranslator.logger.info("Started WebSocket server for Browser Transcriber mode at ${this.address}")
        }

        fun broadcast(op: String, data: JsonObject = JsonObject()) {
            super.broadcast(JsonObject().apply {
                this.addProperty("op", op)
                this.add("d", data)
            }.toString())
        }

        fun WebSocket.sendData(op: String, data: JsonObject = JsonObject()) {
            this.send(JsonObject().apply {
                this.addProperty("op", op)
                this.add("d", data)
            }.toString())
        }
    }
}
