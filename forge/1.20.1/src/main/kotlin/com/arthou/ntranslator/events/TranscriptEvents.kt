package com.arthou.ntranslator.events

import dev.architectury.event.Event
import dev.architectury.event.EventFactory
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.transcript.Transcript

interface TranscriptEvents {
    fun interface Update {
        fun onTranscriptUpdate(transcript: Transcript, language: Language)
    }

    companion object {
        val UPDATE: Event<Update> = EventFactory.createLoop(Update::class.java)
    }
}
