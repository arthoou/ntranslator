package com.arthou.ntranslator.client.transcribers

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.network.UTClientNetworking

abstract class SpeechTranscriber(var language: Language) {
    fun interface TranscriptUpdater {
        fun accept(index: Int, text: String, isFinal: Boolean)
    }

    var lastIndex = 0
    var currentOffset = 0

    lateinit var updater: TranscriptUpdater

    abstract fun stop()
    open fun setMuted(muted: Boolean) {}

    open fun changeLanguage(language: Language) {
        this.language = language
        UTClientNetworking.syncCurrentLanguage()
    }
}
