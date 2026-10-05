package com.arthou.ntranslator.events

import com.arthou.ntranslator.Language
import dev.architectury.event.Event
import dev.architectury.event.EventFactory

interface BrowserSpeechEvents {
    fun interface FinalTranscript {
        fun onFinalTranscript(index: Int, text: String, language: Language)
    }

    fun interface SpeechState {
        fun onSpeechState(speaking: Boolean)
    }

    companion object {
        val FINAL_TRANSCRIPT: Event<FinalTranscript> = EventFactory.createLoop(FinalTranscript::class.java)
        val SPEECH_STATE: Event<SpeechState> = EventFactory.createLoop(SpeechState::class.java)
    }
}
