package com.arthou.ntranslator.client.transcribers.sphinx

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.client.transcribers.SpeechTranscriber

class SphinxSpeechTranscriber(language: Language) : SpeechTranscriber(language) {
    init {
        throw IllegalStateException("PocketSphinx transcription is currently not supported!")
    }

    override fun stop() {
    }
}
