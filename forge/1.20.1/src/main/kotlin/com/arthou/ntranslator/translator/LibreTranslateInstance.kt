package com.arthou.ntranslator.translator

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.random.Weight
import net.minecraft.util.random.WeightedEntry
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.util.HttpHelper

open class LibreTranslateInstance(rawUrl: String, private var weight: Int, val authKey: String? = null) : WeightedEntry {
    val url = normalizeBaseUrl(rawUrl)
    private var cachedSupportedLanguages = HashMultimap.create<Language, Language>()
    var latency: Int = -1
        private set

    var currentlyTranslating = 0

    init {
        val startTime = System.currentTimeMillis()
        if (this.translate("Latency test for NTranslator", Language.ENGLISH, Language.SPANISH) == null)
            throw Exception("Failed to run latency test for LibreTranslate instance $url!")
        latency = (System.currentTimeMillis() - startTime).toInt()
    }

    val supportedLanguages: Multimap<Language, Language>
        get() {
            if (cachedSupportedLanguages.isEmpty) {
                val array = HttpHelper.get("$url/languages").asJsonArray

                for (element in array) {
                    val langData = element.asJsonObject
                    val srcLang = Language.findLibreLang(langData.get("code").asString) ?: continue

                    val targets = langData.getAsJsonArray("targets")
                    for (target in targets) {
                        val targetLang = Language.findLibreLang(target.asString) ?: continue
                        cachedSupportedLanguages.put(srcLang, targetLang)
                    }
                }
            }

            return cachedSupportedLanguages
        }

    fun supportsLanguage(from: Language, to: Language): Boolean {
        if (!supportedLanguages.containsKey(from)) {
            return false
        }

        val supportedTargets = supportedLanguages.get(from)

        return supportedTargets.contains(to)
    }

    fun batchTranslate(texts: List<String>, from: Language, to: Language): List<String>? {
        if (!supportsLanguage(from, to))
            return null

        return try {
            val translated = batchTranslate(from.code, to.code, texts)
            if (translated.size == texts.size) {
                translated
            } else {
                NTranslator.logger.warn("Batch translation at $url returned ${translated.size} result(s) for ${texts.size} request(s). Falling back to per-line translation.")
                fallbackTranslateIndividually(texts, from, to)
            }
        } catch (e: Exception) {
            if (SHOULD_PRINT_ERRORS)
                e.printStackTrace()

            fallbackTranslateIndividually(texts, from, to)
        }
    }

    open fun detectLanguage(text: String): Language? {
        val detected = HttpHelper.post("$url/detect", JsonObject().apply {
            addProperty("q", text)

            if (authKey?.isNotBlank() == true)
                addProperty("api_key", authKey)
        }).asJsonArray.sortedByDescending { it.asJsonObject.get("confidence").asDouble }

        val langCode = detected.firstOrNull()?.asJsonObject?.get("language")?.asString ?: return null
        val lang = Language.findLibreLang(langCode)

        if (lang == null) {
            NTranslator.logger.error("Failed to find language for LibreTranslate code $langCode!")
        }

        return lang
    }

    open fun batchTranslate(from: String, to: String, request: List<String>): List<String> {
        val translated = HttpHelper.post("$url/translate", JsonObject().apply {
            addProperty("source", from)
            addProperty("target", to)
            add("q", JsonArray().apply {
                for (s in request) {
                    this.add(s)
                }
            })

            if (authKey?.isNotBlank() == true)
                addProperty("api_key", authKey)
        }).asJsonObject.get("translatedText")

        return when {
            translated.isJsonArray -> translated.asJsonArray.map { it.asString }
            translated.isJsonPrimitive -> listOf(translated.asString)
            else -> emptyList()
        }
    }

    fun translate(text: String, from: Language, to: Language): String? {
        if (!supportsLanguage(from, to))
            return null

        return try {
            translate(from.code, to.code, text)
        } catch (e: Exception) {
            if (SHOULD_PRINT_ERRORS)
                e.printStackTrace()

            null
        }
    }

    open fun translate(from: String, to: String, request: String): String {
        return HttpHelper.post("$url/translate", JsonObject().apply {
            addProperty("source", from)
            addProperty("target", to)
            addProperty("q", request)
            addProperty("format", "text")

            if (authKey?.isNotBlank() == true)
                addProperty("api_key", authKey)
        })
            .asJsonObject.get("translatedText").asString
    }

    override fun getWeight(): Weight {
        return Weight.of(weight)
    }

    companion object {
        const val MAX_CONCURRENT_TRANSLATIONS = 15
        val SHOULD_PRINT_ERRORS = (System.getProperty("ntranslator.printHttpErrors") == "true") || NTranslator.instance.proxy.isDev

        private fun normalizeBaseUrl(rawUrl: String): String {
            var normalized = rawUrl.trim().trimEnd('/')
            for (suffix in listOf("/translate", "/detect", "/languages")) {
                if (normalized.endsWith(suffix, ignoreCase = true)) {
                    normalized = normalized.dropLast(suffix.length)
                    break
                }
            }

            return normalized.trimEnd('/')
        }
    }

    private fun fallbackTranslateIndividually(texts: List<String>, from: Language, to: Language): List<String>? {
        val translated = mutableListOf<String>()

        for (text in texts) {
            val line = try {
                translate(from.code, to.code, text)
            } catch (e: Exception) {
                if (SHOULD_PRINT_ERRORS)
                    e.printStackTrace()

                return null
            }

            translated.add(line)
        }

        return translated
    }
}
