package com.arthou.ntranslator.config

import com.arthou.ntranslator.Language

data class NTranslatorConfig(
    var configVersion: Int = 7,
    val client: ClientConfig = ClientConfig()
) {
    data class ClientConfig(
        var enabled: Boolean = true,

        // Idioma alvo compartilhado entre livros e placas.
        var subtitleLanguage: Language = Language.ENGLISH
    )
}
