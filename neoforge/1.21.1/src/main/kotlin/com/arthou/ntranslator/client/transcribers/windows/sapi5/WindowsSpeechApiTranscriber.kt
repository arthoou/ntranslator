package com.arthou.ntranslator.client.transcribers.windows.sapi5

import net.minecraft.Util
import org.lwjgl.system.APIUtil
import org.lwjgl.system.SharedLibrary
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.transcribers.SpeechTranscriber

class WindowsSpeechApiTranscriber(language: Language) : SpeechTranscriber(language) {
    init {
    }

    // TODO: need to figure out how to use the Speech Recognition API without using JNI.

    override fun stop() {

    }

    companion object {
        lateinit var library: SharedLibrary

        private var hasTriedLoading = false

        fun isLibraryLoaded(): Boolean {
            return Companion::library.isInitialized
        }

        fun isSupported(): Boolean {
            if (Util.getPlatform() != Util.OS.WINDOWS)
                return false

            //tryLoadingLibrary()
            //return isLibraryLoaded()
            return false
        }

        private fun tryLoadingLibrary() {
            if (!hasTriedLoading && !isLibraryLoaded()) {
                try {
                    library = APIUtil.apiCreateLibrary("sapi.dll")
                } catch (e: Throwable) {
                    NTranslator.logger.error("Failed to load Windows Speech API!")
                    e.printStackTrace()
                }

                hasTriedLoading = true
            }
        }


    }
}
