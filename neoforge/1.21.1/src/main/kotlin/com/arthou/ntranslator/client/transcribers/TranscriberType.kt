package com.arthou.ntranslator.client.transcribers

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.client.transcribers.browser.BrowserSpeechTranscriber
import com.arthou.ntranslator.client.transcribers.sphinx.SphinxSpeechTranscriber
import com.arthou.ntranslator.client.transcribers.windows.sapi5.WindowsSpeechApiTranscriber

enum class TranscriberType(val creator: (Language) -> SpeechTranscriber, val enabled: Boolean = true) {
    SPHINX(::SphinxSpeechTranscriber, false),
    BROWSER(::BrowserSpeechTranscriber),
    WINDOWS_SAPI(::WindowsSpeechApiTranscriber, WindowsSpeechApiTranscriber.isSupported())
}
