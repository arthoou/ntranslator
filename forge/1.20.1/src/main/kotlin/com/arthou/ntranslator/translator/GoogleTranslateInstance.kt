package com.arthou.ntranslator.translator

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.util.HttpHelper
import com.arthou.ntranslator.util.HttpStatusException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections

data class GoogleTranslation(val sourceLanguage: Language, val text: String)

data class GoogleBatchTranslation(val sourceLanguage: Language, val lines: List<String>)

object GoogleTranslateInstance {
    /**
     * O endpoint `gtx` bloqueia o IP com 429 depois de algumas dezenas de chamadas e
     * demora horas para liberar. Estes tres falam a mesma API do Google mas com cotas
     * separadas, entao quando um bloqueia os outros continuam respondendo.
     */
    private enum class Provider(val label: String) {
        /** Retorna `[["traduzido","idioma"], ...]` e aceita varios `q` numa chamada so. */
        CHROME_DICT("clients5/dict-chrome-ex"),

        /** Mesmo formato do gtx, host diferente. */
        TRANSLATE_AT("translate.google.com/at"),

        /** O endpoint original, mantido como ultimo recurso. */
        GTX("translate.googleapis.com/gtx")
    }

    private const val MIN_REQUEST_INTERVAL_MS = 120L
    private const val RETRY_PASS_DELAY_MS = 500L
    private const val CACHE_LIMIT = 512

    // O endpoint do Google recusa User-Agent de biblioteca HTTP (Java/Apache).
    // Sem um UA de navegador a chamada volta 403 e nada e traduzido.
    private val REQUEST_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    private val throttleLock = Any()
    private var nextRequestAt = 0L

    @Volatile
    private var preferredProvider: Provider? = null

    private val translationCache = lruCache<String, String>(CACHE_LIMIT)
    private val detectionCache = lruCache<String, Language>(CACHE_LIMIT)

    private data class RawResult(val detected: Language?, val texts: List<String>)

    // ---------------------------------------------------------------- API

    /** Idioma ja detectado antes para este texto, sem tocar na rede. */
    fun cachedDetection(text: String): Language? = detectionCache[text.trim()]

    fun detectLanguage(text: String): Language? {
        val key = text.trim()
        if (key.isBlank()) {
            return null
        }

        detectionCache[key]?.let { return it }

        val result = fetch("auto", "en", listOf(key)) ?: return null
        return result.detected?.also { detectionCache[key] = it }
    }

    fun translate(text: String, from: Language, to: Language): String? {
        if (from == to) {
            return text
        }

        val key = text.trim()
        if (key.isBlank()) {
            return null
        }

        val source = languageCode(from)
        val target = languageCode(to)
        translationCache[cacheKey(source, target, key)]?.let { return it }

        val result = fetch(source, target, listOf(key)) ?: return null
        val translated = result.texts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null

        translationCache[cacheKey(source, target, key)] = translated
        return translated
    }

    /**
     * Detecta e traduz na mesma chamada (`sl=auto`). Antes eram duas chamadas por texto,
     * o que dobrava o consumo da cota sem nenhum ganho.
     */
    fun translateAuto(text: String, to: Language): GoogleTranslation? {
        val key = text.trim()
        if (key.isBlank()) {
            return null
        }

        val target = languageCode(to)

        cachedAuto(key, to, target)?.let { return it }

        val result = fetch("auto", target, listOf(key)) ?: return null
        val translated = result.texts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null

        remember(key, target, result.detected, translated)
        return GoogleTranslation(result.detected ?: to, translated)
    }

    fun batchTranslate(texts: List<String>, from: Language, to: Language): List<String>? {
        if (texts.isEmpty()) {
            return emptyList()
        }

        if (from == to) {
            return texts
        }

        val source = languageCode(from)
        val target = languageCode(to)
        val keys = texts.map { it.trim() }

        val cached = keys.map { translationCache[cacheKey(source, target, it)] }
        if (cached.all { it != null }) {
            return cached.filterNotNull()
        }

        val result = fetch(source, target, keys) ?: return null
        if (result.texts.size != keys.size) {
            return null
        }

        keys.forEachIndexed { index, key ->
            if (key.isNotBlank()) {
                translationCache[cacheKey(source, target, key)] = result.texts[index]
            }
        }

        return result.texts
    }

    fun batchTranslateAuto(texts: List<String>, to: Language): GoogleBatchTranslation? {
        if (texts.isEmpty()) {
            return null
        }

        val target = languageCode(to)
        val keys = texts.map { it.trim() }

        val cached = keys.map { cachedAuto(it, to, target) }
        if (cached.all { it != null }) {
            return GoogleBatchTranslation(
                cached.first()!!.sourceLanguage,
                cached.map { it!!.text }
            )
        }

        val result = fetch("auto", target, keys) ?: return null
        if (result.texts.size != keys.size) {
            return null
        }

        keys.forEachIndexed { index, key ->
            remember(key, target, result.detected, result.texts[index])
        }

        return GoogleBatchTranslation(result.detected ?: to, result.texts)
    }

    // ------------------------------------------------------------- cache

    private fun cachedAuto(key: String, to: Language, target: String): GoogleTranslation? {
        val detected = detectionCache[key] ?: return null

        if (detected == to) {
            return GoogleTranslation(detected, key)
        }

        val translated = translationCache[cacheKey(languageCode(detected), target, key)] ?: return null
        return GoogleTranslation(detected, translated)
    }

    private fun remember(key: String, target: String, detected: Language?, translated: String) {
        if (key.isBlank() || detected == null) {
            return
        }

        detectionCache[key] = detected
        translationCache[cacheKey(languageCode(detected), target, key)] = translated
    }

    // ------------------------------------------------------------ rede

    /**
     * Percorre os provedores comecando pelo ultimo que funcionou. Um 429 nao espera:
     * ja tenta o proximo host, que tem cota propria.
     */
    private fun fetch(source: String, target: String, texts: List<String>): RawResult? {
        if (texts.isEmpty()) {
            return null
        }

        var lastError: Throwable? = null

        repeat(2) { pass ->
            if (pass > 0 && !sleepQuietly(RETRY_PASS_DELAY_MS)) {
                return null
            }

            for (provider in providerOrder()) {
                throttle()

                try {
                    val result = call(provider, source, target, texts)
                    if (result != null && result.texts.size == texts.size) {
                        if (preferredProvider != provider) {
                            NTranslator.logger.info("NTranslator: usando o tradutor {}.", provider.label)
                            preferredProvider = provider
                        }
                        return result
                    }
                } catch (e: HttpStatusException) {
                    lastError = e
                    NTranslator.logger.warn(
                        "NTranslator: {} respondeu {}, tentando o proximo tradutor.",
                        provider.label, e.code
                    )
                } catch (e: Throwable) {
                    lastError = e
                    NTranslator.logger.warn("NTranslator: falha em {}: {}", provider.label, e.toString())
                }
            }
        }

        NTranslator.logger.error("NTranslator: nenhum tradutor respondeu ($source -> $target).", lastError)
        return null
    }

    private fun providerOrder(): List<Provider> {
        val preferred = preferredProvider ?: return Provider.entries.toList()
        return listOf(preferred) + Provider.entries.filter { it != preferred }
    }

    private fun call(provider: Provider, source: String, target: String, texts: List<String>): RawResult? {
        return when (provider) {
            Provider.CHROME_DICT -> callChromeDict(source, target, texts)
            Provider.TRANSLATE_AT -> callSingle("https://translate.google.com", "at", source, target, texts)
            Provider.GTX -> callSingle("https://translate.googleapis.com", "gtx", source, target, texts)
        }
    }

    /** Aceita varios `q` numa chamada so, entao nao precisa juntar e separar linhas. */
    private fun callChromeDict(source: String, target: String, texts: List<String>): RawResult? {
        val query = texts.joinToString("") { "&q=${encode(it)}" }
        val url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$source&tl=$target$query"
        val response = HttpHelper.get(url, REQUEST_HEADERS)

        if (!response.isJsonArray) {
            return null
        }

        val entries = response.asJsonArray
        val translated = mutableListOf<String>()
        var detected: Language? = null

        for (entry in entries) {
            when {
                // [["traduzido","idioma"], ...]
                entry.isJsonArray -> {
                    val pair = entry.asJsonArray
                    translated += pair.getOrNull(0)?.asStringOrNull() ?: return null
                    if (detected == null) {
                        detected = pair.getOrNull(1)?.asStringOrNull()?.let { Language.findLibreLang(it) }
                    }
                }

                // ["traduzido", ...] quando o idioma de origem foi informado
                entry.isJsonPrimitive -> translated += entry.asStringOrNull() ?: return null

                else -> return null
            }
        }

        return RawResult(detected, translated)
    }

    /** Formato `translate_a/single`: junta as linhas com quebra e separa de volta. */
    private fun callSingle(host: String, client: String, source: String, target: String, texts: List<String>): RawResult? {
        val joined = texts.joinToString("\n")
        val url = "$host/translate_a/single?client=$client&sl=$source&tl=$target&dt=t&q=${encode(joined)}"
        val response = HttpHelper.get(url, REQUEST_HEADERS)

        if (!response.isJsonArray) {
            return null
        }

        val array = response.asJsonArray
        val chunks = array.getOrNull(0)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val combined = buildString {
            for (chunk in chunks) {
                if (!chunk.isJsonArray) continue
                append(chunk.asJsonArray.getOrNull(0)?.asStringOrNull() ?: continue)
            }
        }

        val detected = array.getOrNull(2)?.asStringOrNull()?.let { Language.findLibreLang(it) }
        val split = combined.split("\n").map { it.trim() }

        // Quando o Google junta ou divide linhas o alinhamento quebra; melhor recusar
        // e deixar o chamador tentar outra rota do que devolver texto trocado.
        if (split.size != texts.size) {
            return null
        }

        return RawResult(detected, split)
    }

    /** Espaca as chamadas globalmente para nao estourar o limite por IP. */
    private fun throttle() {
        val waitFor: Long

        synchronized(throttleLock) {
            val now = System.currentTimeMillis()
            val earliest = maxOf(now, nextRequestAt)
            waitFor = earliest - now
            nextRequestAt = earliest + MIN_REQUEST_INTERVAL_MS
        }

        if (waitFor > 0L) {
            sleepQuietly(waitFor)
        }
    }

    private fun sleepQuietly(millis: Long): Boolean {
        return try {
            Thread.sleep(millis)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    // ----------------------------------------------------------- helpers

    private fun encode(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8)

    private fun cacheKey(source: String, target: String, text: String) = "$source|$target|$text"

    private fun <K, V> lruCache(limit: Int): MutableMap<K, V> =
        Collections.synchronizedMap(object : LinkedHashMap<K, V>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > limit
        })

    private fun languageCode(language: Language): String {
        return when (language) {
            Language.CHINESE -> "zh-CN"
            Language.CHINESE_TRADITIONAL -> "zh-TW"
            Language.PORTUGUESE -> "pt-BR"
            Language.PORTUGUESE_PORTUGAL -> "pt-PT"
            else -> language.code
        }
    }

    private fun JsonElement.asStringOrNull(): String? =
        if (isJsonPrimitive) asString else null

    private fun JsonArray.getOrNull(index: Int): JsonElement? =
        if (index in 0 until size()) get(index) else null
}
