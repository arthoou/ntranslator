package com.arthou.ntranslator.translator

 import com.arthou.ntranslator.network.payloads.MarkIncompletePayload
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import net.minecraft.Util
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import org.lwjgl.system.APIUtil
import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.SharedLibrary
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.compat.voicechat.UTVoiceChatCompat
import com.arthou.ntranslator.network.PacketIds
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool

object TranslatorManager {
    private var timer: Timer = Timer("NTranslator Batch Translate Manager")
    internal val queuedTranslations = ConcurrentLinkedQueue<Translation>()

    private val MULTI_ASTERISK_REGEX = Regex("\\*+")
    private val MULTI_MUSIC_NOTE_REGEX = Regex("[♩♪♫♬♭♮♯°ø\u0602≠≭]+")

    var translationPool = ForkJoinPool((Runtime.getRuntime().availableProcessors() - 3).coerceAtLeast(1))

    var instances = ConcurrentLinkedDeque<LibreTranslateInstance>()
        private set

    private fun logInfoSafely(text: String) {
        try {
            NTranslator.logger.info(text)
        } catch (_: Throwable) {
            System.out.println("[NEXEL/INFO] $text")
        }
    }

    private fun logErrorSafely(text: String, error: Throwable? = null) {
        try {
            NTranslator.logger.error(text)
        } catch (_: Throwable) {
            System.err.println("[NEXEL/ERROR] $text")
        }

        error?.printStackTrace()
    }

    private fun useLibreTranslate(): Boolean {
        return NTranslator.config.server.useLibreTranslate
    }

    fun hasReadyInstance(): Boolean {
        return instances.isNotEmpty()
    }

    private fun shouldUseGoogleFallback(): Boolean {
        return !hasReadyInstance()
    }


    fun queueTranslation(line: String, from: Language, to: Language, player: Player, index: Int): CompletableFuture<String> {
        if (shouldUseGoogleFallback()) {
            return CompletableFuture.supplyAsync({
                translateLine(line, from, to) ?: line
            }, translationPool)
        }

        return CompletableFuture<String>().apply {
            val id = "${player.stringUUID}-$index"

            for (previous in queuedTranslations.filter { it.id == id && it.fromLang == from && it.toLang == to }) {
                previous.future.completeExceptionally(Exception("Overridden"))
                queuedTranslations.remove(previous)
            }

            queuedTranslations.add(Translation(
                id,
                line, from, to,
                System.currentTimeMillis(),
                this,
                player, index
            ))
        }
    }

    fun detectLanguage(line: String): Language? {
        if (shouldUseGoogleFallback()) {
            return GoogleTranslateInstance.detectLanguage(line)
        }

        val possible = instances.sortedByDescending { it.weight.asInt() }

        if (possible.isEmpty()) {
            NTranslator.logger.warn("No available instances available for detecting language for line \"$line\"!")
            return null
        }

        for (instance in possible) {
            val lang = instance.detectLanguage(line) ?: continue
            return lang
        }

        NTranslator.logger.warn("Failed to detect language for line \"$line\"!")

        return null
    }

    fun translateLine(line: String, from: Language, to: Language): String? {
        if (shouldUseGoogleFallback()) {
            return GoogleTranslateInstance.translate(line, from, to)
                ?.replace(MULTI_ASTERISK_REGEX, "**")
                ?.replace(MULTI_MUSIC_NOTE_REGEX, "")
        }

        if (instances.isEmpty()) {
            NTranslator.logger.warn("No translation instances are currently ready; delaying translation from $from to $to.")
            return null
        }

        val possible = instances.filter { it.supportsLanguage(from, to) }.sortedByDescending { it.weight.asInt() }

        if (possible.isEmpty()) {
            NTranslator.logger.warn("No instances available for translating $from to $to!)")
            return null
        }

        var index = 0

        for (instance in possible) {
            if (instance.currentlyTranslating >= LibreTranslateInstance.MAX_CONCURRENT_TRANSLATIONS && index++ < possible.size - 1)
                continue

            instance.currentlyTranslating++
            val translated = instance.translate(line, from, to)
            instance.currentlyTranslating--

            if (translated == null) {
                continue
            }

            return translated.replace(MULTI_ASTERISK_REGEX, "**")
                .replace(MULTI_MUSIC_NOTE_REGEX, "")
        }

        NTranslator.logger.warn("Failed to translate $line from $from to $to!")

        return null
    }

    fun batchTranslateLines(lines: List<String>, from: Language, to: Language): List<String>? {
        if (shouldUseGoogleFallback()) {
            return GoogleTranslateInstance.batchTranslate(lines, from, to)
                ?.map {
                    it.replace(MULTI_ASTERISK_REGEX, "**")
                        .replace(MULTI_MUSIC_NOTE_REGEX, "")
                }
        }

        if (instances.isEmpty()) {
            return null
        }

        val possible = instances.filter { it.supportsLanguage(from, to) }.sortedByDescending { it.weight.asInt() }

        if (possible.isEmpty()) {
            NTranslator.logger.warn("No instances available for translating $from to $to!)")
            return null
        }

        var index = 0

        for (instance in possible) {
            if (instance.currentlyTranslating >= LibreTranslateInstance.MAX_CONCURRENT_TRANSLATIONS && index++ < possible.size - 1)
                continue

            instance.currentlyTranslating += lines.size
            val translated = instance.batchTranslate(lines, from, to)
            instance.currentlyTranslating -= lines.size

            if (translated == null) {
                continue
            }

            return translated.map {
                it.replace(MULTI_ASTERISK_REGEX, "**")
                    .replace(MULTI_MUSIC_NOTE_REGEX, "")
            }
        }

        NTranslator.logger.warn("Failed to translate lines from $from to $to:")
        for (line in lines) {
            NTranslator.logger.warn(" - $line")
        }

        return null
    }

    private var isLibraryLoaded = false
    private lateinit var library: SharedLibrary
    private var PFN_cuInit: Long = 0L
    private var PFN_cuDeviceGetCount: Long = 0L
    private var PFN_cuDeviceComputeCapability: Long = 0L

    private var PFN_cuGetErrorName: Long = 0L
    private var PFN_cuGetErrorString: Long = 0L

    private fun logCudaError(code: Int, at: String) {
        if (code == 0)
            return

        // TODO: these return ??? for some reason.
        //       can we figure out why?

        val errorCode = if (PFN_cuGetErrorName != MemoryUtil.NULL) {
            val ptr = MemoryUtil.nmemAlloc(255)
            JNI.callPP(code, ptr, PFN_cuGetErrorName)
            MemoryUtil.memUTF16(ptr).apply {
                MemoryUtil.nmemFree(ptr)
            }
        } else "[CUDA ERROR NAME NOT FOUND]"

        val errorDesc = if (PFN_cuGetErrorString != MemoryUtil.NULL) {
            val ptr = MemoryUtil.nmemAlloc(255)
            JNI.callPP(code, ptr, PFN_cuGetErrorString)
            MemoryUtil.memUTF16(ptr).apply {
                MemoryUtil.nmemFree(ptr)
            }
        } else "[CUDA ERROR DESC NOT FOUND]"

        NTranslator.logger.error("CUDA error at $at: $code $errorCode ($errorDesc)")
    }

    private fun isCudaSupported(): Boolean {
        if (!NTranslator.instance.proxy.isClient()) {
            NTranslator.logger.info("CUDA probing is disabled on dedicated servers.")
            return false
        }

        if (!NTranslator.config.server.shouldUseCuda) {
            NTranslator.logger.info("CUDA is disabled in the config, not enabling CUDA support.")
            return false
        }

        if (!isLibraryLoaded) {
            try {
                library = if (Util.getPlatform() == Util.OS.WINDOWS) {
                    APIUtil.apiCreateLibrary("nvcuda.dll")
                } else if (Util.getPlatform() == Util.OS.LINUX) {
                    APIUtil.apiCreateLibrary("libcuda.so")
                } else {
                    return false
                }

                PFN_cuInit = library.getFunctionAddress("cuInit")
                PFN_cuDeviceGetCount = library.getFunctionAddress("cuDeviceGetCount")
                PFN_cuDeviceComputeCapability = library.getFunctionAddress("cuDeviceComputeCapability")
                PFN_cuGetErrorName = library.getFunctionAddress("cuGetErrorName")
                PFN_cuGetErrorString = library.getFunctionAddress("cuGetErrorString")

                if (PFN_cuInit == MemoryUtil.NULL || PFN_cuDeviceGetCount == MemoryUtil.NULL || PFN_cuDeviceComputeCapability == MemoryUtil.NULL) {
                    // TODO: remove in prod
                    NTranslator.logger.info("CUDA results: $PFN_cuInit $PFN_cuDeviceGetCount $PFN_cuDeviceComputeCapability")
                    return false
                }
            } catch (_: UnsatisfiedLinkError) {
                NTranslator.logger.warn("CUDA library failed to load! Not attempting to initialize CUDA functions.")
                return false
            } catch (e: Throwable) {
                NTranslator.logger.warn("An error occurred while searching for CUDA devices! You don't have to report this, don't worry.")
                e.printStackTrace()
                return false
            }

            isLibraryLoaded = true
        }

        val success = 0

        if (JNI.callI(0, PFN_cuInit).apply {
                logCudaError(this, "init")
            } != success) {
            return false
        }

        val totalPtr = MemoryUtil.nmemAlloc(Int.SIZE_BYTES.toLong())
        if (JNI.callPI(totalPtr, PFN_cuDeviceGetCount).apply {
                logCudaError(this, "get device count")
            } != success) {
            return false
        }

        val totalCudaDevices = MemoryUtil.memGetInt(totalPtr)
        NTranslator.logger.info("Total CUDA devices: $totalCudaDevices")
        if (totalCudaDevices <= 0) {
            return false
        }

        MemoryUtil.nmemFree(totalPtr)

        for (i in 0 until totalCudaDevices) {
            val minorPtr = MemoryUtil.nmemAlloc(Int.SIZE_BYTES.toLong())
            val majorPtr = MemoryUtil.nmemAlloc(Int.SIZE_BYTES.toLong())

            if (JNI.callPPI(majorPtr, minorPtr, i, PFN_cuDeviceComputeCapability).apply {
                    logCudaError(this, "get device compute capability $i")
                } != success) {
                continue
            }

            val majorVersion = MemoryUtil.memGetInt(majorPtr)
            val minorVersion = MemoryUtil.memGetInt(minorPtr)

            MemoryUtil.nmemFree(majorPtr)
            MemoryUtil.nmemFree(minorPtr)

            NTranslator.logger.info("Found device with CUDA compute capability major $majorVersion minor $minorVersion.")

            return true
        }

        return false
    }

    val supportsCuda = isCudaSupported().apply {
        if (this)
            NTranslator.logger.info("CUDA is supported, using GPU for translations.")
        else
            NTranslator.logger.info("CUDA is not supported, using CPU for translations.")
    }

    fun installLibreTranslate(force: Boolean = false) {
        if (!NTranslator.config.server.shouldRunTranslationServer && !force) {
            logInfoSafely("Local LibreTranslate startup skipped because shouldRunTranslationServer is disabled.")
            return
        }

        loadFromConfig()

        LocalLibreTranslateInstance.installLibreTranslate(force)
            .thenAcceptAsync {
                try {
                        LocalLibreTranslateInstance.launchLibreTranslate(it) { instance ->
                            instances.removeIf { existing -> existing is LocalLibreTranslateInstance }
                            instances.addFirst(instance)
                            logInfoSafely("Registered local LibreTranslate instance at ${instance.url}")
                        }
                } catch (e: Throwable) {
                    logErrorSafely("Failed to launch local LibreTranslate instance!", e)
                }
            }
            .exceptionally { error ->
                logErrorSafely("Failed to install local LibreTranslate instance!", error)
                null
            }
    }

    fun init() {
        loadFromConfig()

        LifecycleEvent.SERVER_STARTING.register {
            if (NTranslator.config.server.shouldRunTranslationServer) {
                if (!LocalLibreTranslateInstance.canRunLibreTranslate()) {
                    NTranslator.logger.warn("System resources check reported low availability, but NEXEL will still try to start the local translation server.")
                }

                if (LocalLibreTranslateInstance.isLibreTranslateInstalled() || !NTranslator.instance.proxy.isClient()) {
                    installLibreTranslate()
                } else if (NTranslator.instance.proxy.isClient()) {
                    NTranslatorClient.openDownloadRequest()
                }
            }
        }

        LifecycleEvent.SERVER_STOPPING.register {
            timer.cancel()
            instances.removeIf { it is LocalLibreTranslateInstance }
        }

        PlayerEvent.PLAYER_QUIT.register { player ->
            queuedTranslations.removeIf { it.player.uuid == player.uuid }
        }
    }

    fun loadFromConfig() {
        // Forge shenanigans
        val customThreadPool = ForkJoinPool(1)
        customThreadPool.execute {
            loadFromConfigBlocking()
            customThreadPool.shutdown()
        }
    }

    fun loadFromConfigBlocking() {
        NTranslator.logger.info("Loading NTranslator translation configs...")

        val list = mutableListOf<LibreTranslateInstance>()

        timer.cancel()
        timer = Timer("NTranslator Batch Translate Manager")
        
        translationPool.shutdownNow()
        translationPool = ForkJoinPool((Runtime.getRuntime().availableProcessors() - 3).coerceAtLeast(1))

        for (server in NTranslator.config.server.offloadServers) {
            if (server.url.isBlank()) {
                continue
            }

            try {
                val instance = LibreTranslateInstance(server.url, server.weight, server.authKey?.takeIf { it.isNotBlank() })
                list.add(instance)
            } catch (e: Exception) {
                NTranslator.logger.error("Failed to load an offloaded server instance!")
                e.printStackTrace()
            }
        }

        if (LocalLibreTranslateInstance.currentInstance != null) {
            list.add(0, LocalLibreTranslateInstance.currentInstance!!)
        }

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    if (instances.isEmpty()) {
                        return
                    }

                    val queueLater = ConcurrentLinkedQueue<Translation>()
                    val toTranslate = mutableMapOf<Pair<Language, Language>, MutableList<Translation>>()

                    while (queuedTranslations.isNotEmpty()) {
                        val translation = queuedTranslations.remove()

                        toTranslate.computeIfAbsent(translation.fromLang to translation.toLang) { mutableListOf() }
                            .add(translation)
                    }

                    toTranslate.forEach { (from, to), allTranslations ->
                        val translations = allTranslations.filter {
                            if (it.player is ServerPlayer)
                                !it.player.hasDisconnected()
                            else true
                        }

                        translations.chunked(LibreTranslateInstance.MAX_CONCURRENT_TRANSLATIONS)
                            .forEach { spliced ->
                                CompletableFuture.supplyAsync({
                                    batchTranslateLines(spliced.map { it.text }, from, to)
                                }, translationPool)
                                    .whenCompleteAsync({ t, u ->
                                        spliced.forEachIndexed { i, translation ->
                                            if (t != null && u == null) {
                                                broadcastIncomplete(false, translation)
                                                translation.future.completeAsync { t[i] }
                                            } else {
                                                if (translation.player is ServerPlayer) {
                                                    broadcastIncomplete(true, translation)
                                                }

                                                translation.attempts++
                                                queueLater.add(translation)
                                            }
                                        }
                                    }, translationPool)
                            }
                    }

                    for (translation in queueLater) {
                        if (queuedTranslations.any { it.id == translation.id && it.queueTime > translation.queueTime } || translation.attempts > 3)
                            continue

                        queuedTranslations.add(translation)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, 0L, (NTranslator.config.server.batchTranslateInterval * 1000.0).toLong())

        instances = ConcurrentLinkedDeque(list)

        if (useLibreTranslate()) {
            NTranslator.logger.info("NTranslator translation config successfully loaded!")
        } else if (instances.isEmpty()) {
            NTranslator.logger.info("NTranslator Google Translate mode successfully loaded!")
        } else {
            NTranslator.logger.info("NTranslator Google mode loaded with ready realtime translation instances.")
        }
    }

    private fun broadcastIncomplete(isIncomplete: Boolean, translation: Translation) {
        if (translation.player !is ServerPlayer)
            return

        val source = translation.player

        if (NTranslator.hasVoiceChat) {
            val nearby = UTVoiceChatCompat.getNearbyPlayers(source)

            for (player in nearby) {
                if (UTVoiceChatCompat.isPlayerDeafened(player) && player != source)
                    continue

                NTranslator.instance.proxy.sendPacketServer(
                    player,
                    MarkIncompletePayload(translation.fromLang, translation.toLang, translation.player.uuid, translation.index, isIncomplete)
                )
            }
        } else {
            for (player in source.server.playerList.players) {
                if (player.hasDisconnected())
                    continue

                NTranslator.instance.proxy.sendPacketServer(
                    player,
                    MarkIncompletePayload(translation.fromLang, translation.toLang, translation.player.uuid, translation.index, isIncomplete)
                )
            }
        }
    }
}

