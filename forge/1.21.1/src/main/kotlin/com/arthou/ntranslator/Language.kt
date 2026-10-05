package com.arthou.ntranslator

import net.minecraft.network.chat.Component
import java.util.Locale

enum class Language(val code: String) {
    ENGLISH("en"),
    SPANISH("es"),
    PORTUGUESE("pt"),
    PORTUGUESE_PORTUGAL("pt-pt"),
    FRENCH("fr"),
    SWEDISH("sv"),
    MALAY("ms"),
    HEBREW("he"),
    ARABIC("ar"),
    GERMAN("de"),
    RUSSIAN("ru"),
    JAPANESE("ja"),
    CHINESE("zh"),
    ITALIAN("it"),
    CHINESE_TRADITIONAL("zt"),
    CZECH("cs"),
    DANISH("da"),
    DUTCH("nl"),
    FINNISH("fi"),
    GREEK("el"),
    HINDI("hi"),
    HUNGARIAN("hu"),
    INDONESIAN("id"),
    KOREAN("ko"),
    NORWEGIAN("nb"),
    POLISH("pl"),
    TAGALOG("tl"),
    THAI("th"),
    TURKISH("tr"),
    UKRAINIAN("uk"),
    BULGARIAN("bg"),
    ALBANIAN("sq"),
    AZERBAIJANI("az"),
    BENGALI("bn"),
    BASQUE("eu"),
    CATALAN("ca"),
    ESPERANTO("eo"),
    ESTONIAN("et"),
    GALICIAN("gl"),
    IRISH("ga"),
    KYRGYZ("ky"),
    LATVIAN("lv"),
    LITHUANIAN("lt"),
    PERSIAN("fa"),
    ROMANIAN("ro"),
    SLOVAK("sk"),
    SLOVENIAN("sl"),
    VIETNAMESE("vi"),
    URDU("ur");

    override fun toString(): String {
        return "$name ($code)"
    }

    val text: Component
        get() = Component.translatable("ntranslator.language.$code")

    val displayCode: String
        get() = when (this) {
            PORTUGUESE -> "PT-BR"
            PORTUGUESE_PORTUGAL -> "PT-PT"
            else -> code.uppercase(Locale.ROOT)
        }

    companion object {
        fun findLibreLang(code: String): Language? {
            return when (code.lowercase(Locale.ROOT)) {
                "pt-br", "pt_br", "pb" -> PORTUGUESE
                "pt-pt", "pt_pt" -> PORTUGUESE_PORTUGAL
                "fil", "fil-ph", "tl-ph" -> TAGALOG
                "zh", "zh-hans", "zh-cn" -> CHINESE
                "zt", "zh-hant", "zh-tw" -> CHINESE_TRADITIONAL
                else -> entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
            }
        }
    }
}
