package com.arthou.ntranslator.client.translation

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.translator.GoogleTranslateInstance
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object ClientTranslationHelper {
    // O pool comum do ForkJoin disparava uma requisicao por pagina ao mesmo tempo,
    // o que fazia o Google responder 429 e devolver a frase sem traducao.
    // Duas threads dedicadas bastam e mantem as chamadas espacadas.
    private val pool = Executors.newFixedThreadPool(2, object : ThreadFactory {
        private val counter = AtomicInteger(1)

        override fun newThread(runnable: Runnable) = Thread(runnable, "NTranslator Client Translator ${counter.getAndIncrement()}").apply {
            isDaemon = true
        }
    })

    fun translateTextAsync(text: String, targetLanguage: Language): CompletableFuture<TranslatedText> {
        return CompletableFuture.supplyAsync({
            translateText(text, targetLanguage)
        }, pool)
    }

    fun translateLinesAsync(lines: List<String>, targetLanguage: Language): CompletableFuture<TranslatedLines> {
        return CompletableFuture.supplyAsync({
            translateLines(lines, targetLanguage)
        }, pool)
    }

    /** Idioma ja conhecido para este texto, sem chamada de rede. */
    fun cachedLanguage(text: String): Language? = GoogleTranslateInstance.cachedDetection(text)

    fun detectLanguage(text: String): Language {
        return GoogleTranslateInstance.detectLanguage(text)
            ?: Language.ENGLISH
    }

    fun translateText(text: String, targetLanguage: Language): TranslatedText {
        if (text.isBlank()) {
            return TranslatedText(targetLanguage, text, complete = true)
        }

        // Uma unica chamada com sl=auto ja devolve o idioma detectado e o texto
        // traduzido; antes eram duas chamadas separadas por pagina.
        val result = GoogleTranslateInstance.translateAuto(text, targetLanguage)
            ?: return TranslatedText(targetLanguage, text, complete = false)

        return TranslatedText(result.sourceLanguage, result.text, complete = true)
    }

    fun translateLines(lines: List<String>, targetLanguage: Language): TranslatedLines {
        if (lines.none { it.isNotBlank() }) {
            return TranslatedLines(targetLanguage, lines, complete = true)
        }

        val indexed = lines.mapIndexedNotNull { index, line ->
            if (line.isBlank()) null else index to line
        }

        val batch = GoogleTranslateInstance.batchTranslateAuto(indexed.map { it.second }, targetLanguage)
            ?: return TranslatedLines(targetLanguage, lines, complete = false)

        val translated = lines.toMutableList()
        indexed.forEachIndexed { translatedIndex, (originalIndex, originalLine) ->
            translated[originalIndex] = batch.lines.getOrElse(translatedIndex) { originalLine }
        }

        return TranslatedLines(batch.sourceLanguage, translated, complete = true)
    }
}

/** [complete] e falso quando a traducao falhou e [text] e o original, para permitir nova tentativa. */
data class TranslatedText(val sourceLanguage: Language, val text: String, val complete: Boolean = true)

/** [complete] e falso quando a traducao falhou e [lines] sao as originais. */
data class TranslatedLines(val sourceLanguage: Language, val lines: List<String>, val complete: Boolean = true)
